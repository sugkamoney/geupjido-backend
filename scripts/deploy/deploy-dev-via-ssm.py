#!/usr/bin/env python3
"""Upload a verified JAR and deploy it through Systems Manager Run Command."""

import argparse
import hashlib
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path
from urllib.request import urlopen


API_BASE = "https://dev-api.geupjido.site"
TERMINAL_STATUSES = {
    "Success", "Failed", "Cancelled", "TimedOut", "DeliveryTimedOut",
    "ExecutionTimedOut", "Undeliverable", "Terminated",
}


def aws(*arguments):
    try:
        result = subprocess.run(
            ["aws", *arguments], capture_output=True, text=True, check=False
        )
    except FileNotFoundError as error:
        raise RuntimeError("AWS CLI를 찾을 수 없습니다.") from error
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "AWS CLI 명령이 실패했습니다.")
    return result.stdout.strip()


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def remote_deploy_command(bucket, commit, region, jar_hash, script_hash):
    prefix = f"s3://{bucket}/dev-releases/{commit}"
    jar_uri = shlex.quote(f"{prefix}/application.jar")
    script_uri = shlex.quote(f"{prefix}/release-dev-service.sh")
    region_arg = shlex.quote(region)
    return "\n".join([
        "set -eu",
        "release_dir=$(mktemp -d /tmp/geupjido-release.XXXXXX)",
        'trap \'rm -rf "$release_dir"\' EXIT',
        f'aws s3 cp {jar_uri} "$release_dir/application.jar" --region {region_arg} --only-show-errors',
        f'aws s3 cp {script_uri} "$release_dir/release-dev-service.sh" --region {region_arg} --only-show-errors',
        f'printf "%s  %s\\n" {shlex.quote(jar_hash)} "$release_dir/application.jar" | sha256sum --check --status',
        f'printf "%s  %s\\n" {shlex.quote(script_hash)} "$release_dir/release-dev-service.sh" | sha256sum --check --status',
        'bash "$release_dir/release-dev-service.sh" deploy "$release_dir/application.jar"',
    ])


def remote_rollback_command(bucket, commit, region, script_hash):
    script_uri = shlex.quote(
        f"s3://{bucket}/dev-releases/{commit}/release-dev-service.sh"
    )
    return "\n".join([
        "set -eu",
        "release_dir=$(mktemp -d /tmp/geupjido-rollback.XXXXXX)",
        'trap \'rm -rf "$release_dir"\' EXIT',
        f'aws s3 cp {script_uri} "$release_dir/release-dev-service.sh" --region {shlex.quote(region)} --only-show-errors',
        f'printf "%s  %s\\n" {shlex.quote(script_hash)} "$release_dir/release-dev-service.sh" | sha256sum --check --status',
        'bash "$release_dir/release-dev-service.sh" rollback',
    ])


def send_command(instance_id, region, command, comment):
    parameters = json.dumps({"commands": [command], "executionTimeout": ["600"]})
    return aws(
        "ssm", "send-command",
        "--instance-ids", instance_id,
        "--document-name", "AWS-RunShellScript",
        "--comment", comment,
        "--parameters", parameters,
        "--timeout-seconds", "120",
        "--region", region,
        "--query", "Command.CommandId",
        "--output", "text",
    )


def wait_for_command(command_id, instance_id, region, timeout_seconds=720):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            invocation = json.loads(aws(
                "ssm", "get-command-invocation",
                "--command-id", command_id,
                "--instance-id", instance_id,
                "--region", region,
                "--output", "json",
            ))
        except RuntimeError as error:
            # Run Command is eventually consistent immediately after send-command.
            if "InvocationDoesNotExist" not in str(error):
                raise
            time.sleep(3)
            continue
        status = invocation["Status"]
        if status in TERMINAL_STATUSES:
            for field in ("StandardOutputContent", "StandardErrorContent"):
                if invocation.get(field):
                    print(invocation[field], file=sys.stderr if field == "StandardErrorContent" else sys.stdout)
            if status != "Success":
                raise RuntimeError(f"SSM 명령 실패: {status} ({command_id})")
            return
        time.sleep(3)
    raise TimeoutError(f"SSM 명령 완료를 기다리는 시간이 초과됐습니다: {command_id}")


def verify_public_api():
    with urlopen(f"{API_BASE}/actuator/health", timeout=10) as response:
        if json.load(response).get("status") != "UP":
            raise RuntimeError("외부 health가 UP이 아닙니다.")
    with urlopen(f"{API_BASE}/v3/api-docs", timeout=10) as response:
        if not json.load(response).get("paths"):
            raise RuntimeError("외부 OpenAPI에 경로가 없습니다.")


def deploy(jar, release_script, bucket, instance_id, region, commit):
    if not jar.is_file() or not release_script.is_file():
        raise ValueError("배포 JAR 또는 릴리스 스크립트를 찾을 수 없습니다.")
    if not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", bucket):
        raise ValueError("S3 버킷 이름이 올바르지 않습니다.")
    if not re.fullmatch(r"i-[0-9a-f]{8,17}", instance_id):
        raise ValueError("EC2 인스턴스 ID가 올바르지 않습니다.")
    if not re.fullmatch(r"[a-z]{2}-[a-z]+-\d", region):
        raise ValueError("AWS 리전이 올바르지 않습니다.")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Git 커밋 SHA가 올바르지 않습니다.")

    jar_hash = digest(jar)
    script_hash = digest(release_script)
    prefix = f"s3://{bucket}/dev-releases/{commit}"
    aws("s3", "cp", str(jar), f"{prefix}/application.jar", "--region", region, "--only-show-errors")
    aws("s3", "cp", str(release_script), f"{prefix}/release-dev-service.sh", "--region", region, "--only-show-errors")

    command = remote_deploy_command(bucket, commit, region, jar_hash, script_hash)
    command_id = send_command(instance_id, region, command, f"geupjido dev deploy {commit[:12]}")
    print(f"배포 SSM 명령: {command_id}", flush=True)
    wait_for_command(command_id, instance_id, region)

    try:
        verify_public_api()
    except Exception as error:
        rollback_id = send_command(
            instance_id, region,
            remote_rollback_command(bucket, commit, region, script_hash),
            f"geupjido dev rollback {commit[:12]}",
        )
        print(f"외부 검증 실패, 롤백 SSM 명령: {rollback_id}", file=sys.stderr, flush=True)
        try:
            wait_for_command(rollback_id, instance_id, region)
        except Exception as rollback_error:
            raise RuntimeError(f"외부 검증 실패: {error}; 롤백 실패: {rollback_error}") from rollback_error
        raise RuntimeError(f"외부 검증 실패로 이전 JAR 복구: {error}") from error
    print(f"개발 서버 배포 및 외부 검증 완료: {commit}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--release-script", required=True, type=Path)
    parser.add_argument("--bucket", required=True)
    parser.add_argument("--instance-id", required=True)
    parser.add_argument("--region", required=True)
    parser.add_argument("--commit", required=True)
    args = parser.parse_args()
    try:
        deploy(args.jar, args.release_script, args.bucket, args.instance_id, args.region, args.commit)
    except (RuntimeError, TimeoutError, ValueError) as error:
        parser.exit(1, f"배포 실패: {error}\n")


if __name__ == "__main__":
    main()
