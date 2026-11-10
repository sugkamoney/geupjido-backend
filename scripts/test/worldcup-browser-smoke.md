# 월드컵 브라우저 쿠키·CORS 확인

이 절차는 실제 브라우저에서 익명 쿠키, 선택 저장, 새로고침 후 이어하기, 허용하지 않은 출처 차단을 확인한다. `worldcup-browser-smoke.html`은 검증용 화면이며 서비스 프런트엔드 코드는 아니다.

HTML 파일을 `file://` 주소로 직접 열면 검증할 수 없다. 아래 서버를 실행한 뒤 `http://localhost:5173/worldcup-browser-smoke.html`로 접속한다. 검증이 끝나 서버를 종료했다면 다시 실행해야 한다.

## 준비

1. 로컬 Docker의 `geupjido-postgres`가 실행 중인지 확인한다.
2. 저장소 루트에서 `bash scripts/test/worldcup-browser-smoke-db.sh create`를 실행한다. 현재 `geupjido` DB를 읽어 별도 `geupjido_worldcup_browser_smoke` DB를 만든다. 기존 DB의 게임·투표는 변경하지 않는다.
3. 백엔드를 별도 터미널에서 실행한다.

   ```bash
   DB_URL=jdbc:postgresql://localhost:5433/geupjido_worldcup_browser_smoke \
   SERVER_PORT=18080 ./gradlew bootRun
   ```

4. 검증 화면을 별도 터미널 두 개에서 실행한다.

   ```bash
   python3 -m http.server 5173 --bind localhost --directory scripts/test
   python3 -m http.server 5176 --bind localhost --directory scripts/test
   ```

## 확인

1. 새 브라우저 프로필에서 `http://localhost:5173/worldcup-browser-smoke.html`을 연다. 같은 호스트의 기존 `gjd_uid`가 남지 않은 상태에서 시작한다.
2. **게임 시작 → 1표 저장 → 재조회**를 누른다. PASS, `nextRoundNumber: 2`, 선택 기록 1개, 대진 동일 여부 `true`, `cookieVisibleToJavaScript: false`를 확인한다. 개발자 도구의 첫 시작 응답에는 `Set-Cookie: gjd_uid=...; HttpOnly; SameSite=Lax`가 있어야 한다. 식별자 값은 기록하지 않는다.
3. 페이지를 새로고침하고 **새로고침 후 이어하기 확인**을 누른다. PASS와 저장된 선택 1개를 확인한다.
4. 같은 브라우저에서 `http://localhost:5176/worldcup-browser-smoke.html`을 열고 **허용하지 않은 출처 확인**을 누른다. PASS와 브라우저 콘솔의 CORS 차단 메시지를 확인한다.
5. 백엔드를 종료한 뒤 `bash scripts/test/worldcup-browser-smoke-db.sh drop`으로 검증 DB를 정리한다. 정리 전에는 검증 DB의 `worldcup_game`, `worldcup_vote`에서 실행 결과를 확인할 수 있다.

`localhost`와 `127.0.0.1`을 섞으면 쿠키 호스트가 달라진다. 다른 API 포트를 쓰는 경우 검증 화면의 **API 주소**를 함께 바꾼다.

## 2026-11-10 검증 결과

로컬 Chrome의 새 프로필과 별도 DB에서 세 흐름을 실행했다. 5173 출처의 새 게임 생성, 1표 저장과 재조회가 PASS였고, 같은 프로필로 페이지를 다시 열었을 때도 다음 판 번호 2와 선택 1개가 유지됐다. `gjd_uid`는 JavaScript에서 보이지 않았다. 5176 출처는 브라우저 `Failed to fetch`로 차단됐으며 서버 응답도 403, `Access-Control-Allow-Origin` 없음으로 확인했다. 검증 DB에는 게임 1개와 선택 1개가 생겼고, 원본 DB의 두 테이블은 각각 0개를 유지했다.
