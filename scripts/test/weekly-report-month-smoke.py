#!/usr/bin/env python3
"""로컬 DB 복제본에서 4주 주간 리포트 통합 검증을 실행한다."""

import os
import subprocess
import sys
import xml.etree.ElementTree as xml
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DB_NAME = "geupjido_weekly_report_month_smoke"
DB_SCRIPT = ROOT / "scripts/test/worldcup-browser-smoke-db.sh"
TEST_NAME = "com.geupjido.report.WeeklyReportMonthSimulationIT"


def run(*args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def database_exists():
    result = run(
        "docker", "exec", "-u", "postgres", "geupjido-postgres",
        "psql", "-d", "postgres", "-Atc",
        f"SELECT 1 FROM pg_database WHERE datname = '{DB_NAME}'",
        capture_output=True, text=True,
    )
    return result.stdout.strip() == "1"


def source_snapshot():
    result = run(
        "docker", "exec", "-u", "postgres", "geupjido-postgres",
        "psql", "-d", "geupjido", "-Atc",
        "SELECT (SELECT count(*) FROM worldcup_vote), "
        "(SELECT count(*) FROM weekly_report), "
        "(SELECT count(*) FROM weekly_report_zone_snapshot)",
        capture_output=True, text=True,
    )
    return result.stdout.strip()


def main():
    if database_exists():
        raise RuntimeError(f"검증 DB가 이미 있습니다: {DB_NAME}")

    before = source_snapshot()
    clone_attempted = False
    try:
        clone_attempted = True
        run("bash", str(DB_SCRIPT), "create", DB_NAME)

        # 상위 셸의 datasource/프로필 설정이 복제 DB 경로를 바꾸지 못하게 한다.
        env = {key: os.environ[key] for key in
               ("PATH", "JAVA_HOME", "HOME", "TMPDIR", "LANG", "LC_ALL", "TZ")
               if key in os.environ}
        env.update({
            "DB_URL": f"jdbc:postgresql://127.0.0.1:5433/{DB_NAME}",
            "DB_USERNAME": "geupjido",
            "DB_PASSWORD": "geupjido-local",
            "RUN_POSTGIS_IT": "true",
            "WEEKLY_REPORT_SCHEDULE_ENABLED": "false",
            "WORLDCUP_RATING_SCHEDULE_ENABLED": "false",
        })
        run("./gradlew", "test", "--tests", TEST_NAME,
            "--no-daemon", "--max-workers=1", "--rerun-tasks", env=env)
        result_xml = ROOT / "build/test-results/test/TEST-com.geupjido.report.WeeklyReportMonthSimulationIT.xml"
        output = xml.parse(result_xml).getroot().findtext("system-out") or ""
        for line in output.splitlines():
            if line.startswith(("REPORT_MONTH_WEEK ", "REPORT_MONTH_SUMMARY ")):
                print(line, flush=True)
    finally:
        if clone_attempted:
            run("bash", str(DB_SCRIPT), "drop", DB_NAME)
        after = source_snapshot()
        if after != before:
            raise RuntimeError(
                f"원본 로컬 DB 집계가 변경됐습니다: 전={before}, 후={after}"
            )
        print(f"원본 DB 집계 유지 확인: {after}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
