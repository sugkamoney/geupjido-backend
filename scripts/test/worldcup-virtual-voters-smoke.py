#!/usr/bin/env python3
"""로컬 격리 DB에서 HTTP 투표와 예약 발행을 끝까지 검증한다."""

import http.cookiejar
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DB_SCRIPT = ROOT / "scripts/test/worldcup-browser-smoke-db.sh"
DB_NAME = "geupjido_worldcup_virtual_voters"
JAR = ROOT / "build/libs/geupjido-backend-0.0.1-SNAPSHOT.jar"
BASE_URL = "http://localhost:18080"
VIRTUAL_VISITORS = 10
VOTES_PER_VISITOR = 9
PUBLISH_TIMEOUT_SECONDS = 150


class SmokeFailure(Exception):
    pass


def api(opener, method, path, payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        BASE_URL + path,
        data=body,
        headers={"Content-Type": "application/json"} if body is not None else {},
        method=method,
    )
    try:
        with opener.open(request, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            detail = json.load(error)
        except ValueError:
            detail = {}
        raise SmokeFailure(
            f"{method} {path}: HTTP {error.code} {detail.get('code', '')}"
        ) from error


def require(condition, message):
    if not condition:
        raise SmokeFailure(message)


def wait_for_start(process, opener):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        require(process.poll() is None, "로컬 앱이 시작 전에 종료됐습니다.")
        try:
            _, status = api(opener, "GET", "/api/v1/map/status")
            return status
        except urllib.error.URLError:
            time.sleep(1)
    raise SmokeFailure("로컬 앱이 60초 안에 응답하지 않았습니다.")


def reset_cloned_votes():
    # 원본 로컬 DB의 기존 선택은 복제될 수 있다. 복제본에서만 이를 지워 수치를 고정한다.
    statement = (
        "TRUNCATE worldcup_vote, worldcup_skip, worldcup_game RESTART IDENTITY; "
        "DELETE FROM worldcup_zone_vote_status; "
        "UPDATE worldcup_rating_publication "
        "SET last_completed_at = NULL, valid_vote_count = 0, participant_count = 0 "
        "WHERE id = 1;"
    )
    subprocess.run(
        ["docker", "exec", "-u", "postgres", "geupjido-postgres",
         "psql", "-d", DB_NAME, "-v", "ON_ERROR_STOP=1", "-c", statement],
        check=True,
    )


def vote_as_virtual_visitors(opener):
    _, zones = api(opener, "GET", "/api/v1/worldcup/zones")
    zone = None
    for item in zones:
        if not item["canStart"] or item["activeComplexCount"] < 4:
            continue
        _, detail = api(opener, "GET", f"/api/v1/map/zones/{item['zoneId']}")
        if detail["zone"]["rankingBasis"] == "INITIAL":
            zone = item
            break
    require(zone is not None, "투표를 시작할 수 있는 권역이 없습니다.")
    zone_id = zone["zoneId"]
    cookie_values = set()
    cross_zone_votes = 0

    for visitor_number in range(1, VIRTUAL_VISITORS + 1):
        jar = http.cookiejar.CookieJar()
        visitor = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
        code, game = api(visitor, "POST", f"/api/v1/worldcup/zones/{zone_id}/games")
        require(code == 201 and len(game["matchups"]) == 10,
                f"방문자 {visitor_number}: 새 게임 10판을 받지 못했습니다.")
        cookies = [cookie.value for cookie in jar if cookie.name == "gjd_uid"]
        require(len(cookies) == 1, f"방문자 {visitor_number}: 익명 쿠키가 없습니다.")
        cookie_values.add(cookies[0])

        for round_number, matchup in enumerate(game["matchups"][:VOTES_PER_VISITOR], 1):
            if matchup["type"] == "CROSS_ZONE":
                cross_zone_votes += 1
            selected_id = matchup["left"]["complexId"]
            _, result = api(
                visitor,
                "PUT",
                f"/api/v1/worldcup/games/current/rounds/{round_number}/vote",
                {"selectedComplexId": selected_id},
            )
            require(result["nextRoundNumber"] == round_number + 1
                    and len(result["selections"]) == round_number,
                    f"방문자 {visitor_number}: {round_number}판 선택이 저장되지 않았습니다.")

        _, current = api(visitor, "GET", "/api/v1/worldcup/games/current")
        require(current["nextRoundNumber"] == VOTES_PER_VISITOR + 1
                and len(current["selections"]) == VOTES_PER_VISITOR,
                f"방문자 {visitor_number}: 쿠키로 저장된 게임을 재조회하지 못했습니다.")
        print(f"HTTP 투표 완료: 방문자 {visitor_number}/{VIRTUAL_VISITORS}", flush=True)

    require(len(cookie_values) == VIRTUAL_VISITORS, "방문자 쿠키가 서로 다르지 않습니다.")
    require(cross_zone_votes >= 20, "권역 간 선택이 전환 조건인 20표에 못 미칩니다.")
    return zone_id, cross_zone_votes


def wait_for_publication(opener, zone_id, submitted_at):
    deadline = time.monotonic() + PUBLISH_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        _, status = api(opener, "GET", "/api/v1/map/status")
        completed_text = status["lastCompletedAt"]
        if completed_text is not None:
            completed_at = datetime.fromisoformat(completed_text.replace("Z", "+00:00"))
            if completed_at > submitted_at and status["validVoteCount"] == 90 \
                    and status["participantCount"] == VIRTUAL_VISITORS:
                _, detail = api(opener, "GET", f"/api/v1/map/zones/{zone_id}")
                zone = detail["zone"]
                ranked = [item for item in detail["complexes"]
                          if item["zoneRank"] is not None and item["score"] is not None]
                if zone["rankingBasis"] == "VOTE" and zone["voteTier"] is not None \
                        and zone["validVoteCount"] == 90 and ranked:
                    return status, zone, ranked
        time.sleep(2)
    raise SmokeFailure("예약 집계 후 150초 안에 유효표·VOTE 급지·단지 순위를 확인하지 못했습니다.")


def stop_app(process):
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def main():
    with socket.socket() as sock:
        if sock.connect_ex(("127.0.0.1", 18080)) == 0:
            raise SmokeFailure("18080 포트를 다른 프로세스가 사용 중입니다.")

    print("애플리케이션 JAR 빌드 중", flush=True)
    subprocess.run(["./gradlew", "bootJar", "--no-daemon", "--max-workers=1"],
                   cwd=ROOT, check=True)
    require(JAR.is_file(), "빌드된 JAR를 찾지 못했습니다.")

    database_created = False
    app = None
    with tempfile.TemporaryFile(mode="w+") as app_log:
        try:
            print(f"로컬 검증 DB 생성 중: {DB_NAME}", flush=True)
            subprocess.run(["bash", str(DB_SCRIPT), "create", DB_NAME], cwd=ROOT, check=True)
            database_created = True
            reset_cloned_votes()

            # 상위 셸의 SPRING_DATASOURCE_URL 등이 DB_URL보다 우선하지 않도록
            # 실행에 필요한 시스템 변수만 넘긴다.
            env = {key: os.environ[key] for key in
                   ("PATH", "JAVA_HOME", "HOME", "TMPDIR", "LANG", "LC_ALL", "TZ")
                   if key in os.environ}
            env.update({
                "SPRING_PROFILES_ACTIVE": "dev",
                "DB_URL": f"jdbc:postgresql://127.0.0.1:5433/{DB_NAME}",
                "DB_USERNAME": "geupjido",
                "DB_PASSWORD": "geupjido-local",
                "REDIS_HOST": "127.0.0.1",
                "SERVER_PORT": "18080",
                "SERVER_ADDRESS": "127.0.0.1",
                "ANONYMOUS_COOKIE_SECURE": "false",
                "WORLDCUP_RATING_SCHEDULE_ENABLED": "true",
                "WORLDCUP_RATING_SCHEDULE_CRON": "*/15 * * * * *",
            })
            app = subprocess.Popen(["java", "-jar", str(JAR)], cwd=ROOT,
                                   env=env, stdout=app_log, stderr=subprocess.STDOUT)
            opener = urllib.request.build_opener()
            initial_status = wait_for_start(app, opener)
            require(initial_status["nextScheduledAt"] is not None,
                    "dev 프로필에서 예약 집계가 활성화되지 않았습니다.")
            require(initial_status["validVoteCount"] == 0
                    and initial_status["participantCount"] == 0,
                    "검증 DB에 기존 유효표가 남아 있습니다.")

            zone_id, cross_zone_votes = vote_as_virtual_visitors(opener)
            submitted_at = datetime.now(timezone.utc)
            print("예약 발행과 지도 응답을 기다리는 중", flush=True)
            status, zone, ranked = wait_for_publication(opener, zone_id, submitted_at)
            print("PASS: HTTP 쿠키 투표 → 예약 발행 → 지도 급지·단지 순위")
            print(f"가상 방문자 {VIRTUAL_VISITORS}명, 선택 {VIRTUAL_VISITORS * VOTES_PER_VISITOR}표, "
                  f"권역 간 선택 {cross_zone_votes}표")
            print(f"발행 유효표 {status['validVoteCount']}표, 참여자 {status['participantCount']}명, "
                  f"검증 권역 유효표 {zone['validVoteCount']}표, 순위 단지 {len(ranked)}곳")
        except (SmokeFailure, subprocess.CalledProcessError, urllib.error.URLError,
                TimeoutError, KeyError, ValueError) as error:
            app_log.flush()
            app_log.seek(0)
            tail = app_log.readlines()[-35:]
            print(f"FAIL: {error}", file=sys.stderr)
            if tail:
                print("애플리케이션 로그 마지막 35줄:", file=sys.stderr)
                print("".join(tail), file=sys.stderr)
            return 1
        finally:
            stop_app(app)
            if database_created:
                print(f"로컬 검증 DB 정리 중: {DB_NAME}", flush=True)
                subprocess.run(["bash", str(DB_SCRIPT), "drop", DB_NAME], cwd=ROOT, check=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SmokeFailure as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
