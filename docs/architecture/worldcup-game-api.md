# 월드컵 게임 시작·선택·건너뛰기·이어하기 API

- 관련 이슈: [#119](https://github.com/sugkamoney/geupjido-backend/issues/119), [#122](https://github.com/sugkamoney/geupjido-backend/issues/122), [#130](https://github.com/sugkamoney/geupjido-backend/issues/130), [#133](https://github.com/sugkamoney/geupjido-backend/issues/133)
- 대진 선정 기준: [월드컵 10판 대진 생성](worldcup-match-generation.md)
- 카드 필드 기준: [월드컵 카드 정보 계약](worldcup-card-api-contract.md)
- 권역 선택 목록과 인접 대체 권역 조회: [월드컵 권역 선택 API](worldcup-zone-selection-api.md)

## 게임과 익명 방문자

회원가입이나 로그인 없이 서버가 UUID v4 형식의 익명 식별자를 발급한다. 브라우저에는 `gjd_uid` 쿠키로 저장하며, 응답 본문에는 방문자 식별자를 넣지 않는다. 쿠키는 `HttpOnly`, `SameSite=Lax`, `Path=/`, 수명 365일이다. `Secure`는 로컬에서 끄고 dev·prod에서 켠다. dev 환경만 `ANONYMOUS_COOKIE_SECURE`로 조정할 수 있다.

게임별 내부 번호는 `worldcup_game.id`로만 관리한다. 외부 API에는 게임 번호를 노출하지 않는다. 같은 익명 식별자에게 미완료 게임이 있으면 새 시작 요청에서도 그 게임을 반환한다. 10판을 모두 선택하거나 건너뛰어 완료한 뒤에는 새 스냅샷을 만들 수 있다. 재조회와 판 확정은 가장 최근 게임을 대상으로 한다.

## 요청 흐름

1. `POST /api/v1/worldcup/games`는 배너 기준 권역을 추첨해 게임을 시작한다.
2. `POST /api/v1/worldcup/zones/{zoneId}/games`는 선택한 권역에서 게임을 시작한다.
3. 첫 시작 요청에 유효한 `gjd_uid`가 없으면 서버가 발급하고 `Set-Cookie`로 돌려준다. 기존 쿠키가 있으면 같은 익명 식별자를 사용한다.
4. 두 시작 API는 미완료 게임이 없을 때만 #116의 대진 생성 결과를 JSONB 스냅샷으로 DB에 저장하고 `201 Created`와 10판을 반환한다. 미완료 게임이 있으면 새 대진을 만들지 않고 `200 OK`로 저장된 진행 상태를 반환한다. 선택한 권역 진입에서도 기존 미완료 게임이 우선한다.
5. `GET /api/v1/worldcup/games/current`는 쿠키 소유자의 최신 대진·선택·건너뛰기·진행 상태를 반환한다. 없거나 쿠키가 유효하지 않으면 `404 GAME_NOT_FOUND`다.
6. `PUT /api/v1/worldcup/games/current/rounds/{roundNumber}/vote`의 JSON 본문 `{"selectedComplexId":"단지ID"}`로 다음 판을 선택한다. 서버는 저장된 대진의 두 단지 중 하나인지 검증하고 1번부터 순서대로 저장한다. 같은 선택 재요청은 기존 결과를 반환하고 표를 추가하지 않는다. 이미 고른 판을 다른 단지로 바꿀 수 없다.
7. `PUT /api/v1/worldcup/games/current/rounds/{roundNumber}/skip`은 본문 없이 다음 판을 건너뛴다. 건너뛰기는 표를 만들지 않으며, 같은 판 재요청은 기존 상태를 반환한다. 건너뛴 판은 다시 선택할 수 없고 선택한 판도 건너뛸 수 없다.
8. 선택 또는 건너뛰기 직후 응답의 `roundResult`로 그 대결의 비율 표시 가능 여부를 전달한다. 화면은 비율을 잠깐 보여준 뒤 다음 판으로 진행할 수 있다.
9. 10번 판까지 확정하면 게임을 완료한다. 다시 조회해도 선택·건너뛰기·완료 상태가 유지된다.

## 하루 게임 시작 한도

익명 방문자별로 한국 시간(`Asia/Seoul`) 날짜에 새로 생성한 게임을 최대 6개까지 허용한다. `worldcup_game.started_at`이 해당 날짜의 00:00 이상, 다음 날짜 00:00 미만인 행을 센다. 완료 여부와 관계없이 시작한 게임은 1개로 계산하며, 진행 중인 게임을 이어가기 위해 시작 요청을 반복해도 추가로 세지 않는다. 한도에 도달하면 새 대진을 생성하지 않고 `429 DAILY_GAME_LIMIT_REACHED`를 반환한다. 한국 시간 자정부터 새 날짜의 한도를 적용한다.

실제 저장 직전에 익명 방문자별 DB 잠금을 잡고 한도를 다시 확인하므로 동시 요청으로 6개를 초과해 저장하지 않는다. 잠금 뒤에 확인한 시각을 게임 시작 시각으로 함께 저장해 자정 경계에서도 같은 날짜에 집계한다. 브라우저 쿠키를 지우거나 바꾸는 행위까지 막는 사용자 계정 기반 제한은 아니다.

새 게임 생성 응답(`201`)의 `Location`은 `/api/v1/worldcup/games/current`다. 시작과 재조회 응답 모두 `Cache-Control: no-store`를 사용한다. 시작 가능한 대진이 부족하면 `409 GAME_UNAVAILABLE`이며 게임과 새 쿠키를 저장하지 않는다.

## 응답 구조

`baseZoneId`, `tierRangeUsed`, `matchups`, `selections`, `skips`, `nextRoundNumber`, `completed`를 반환한다. `matchups`는 표시 순서대로 10개이며 각 항목의 `roundNumber`는 1부터 10까지다. `type`은 `CROSS_ZONE` 또는 `WITHIN_ZONE`이고 `left`와 `right`는 #113 카드 계약의 단지 ID, 이름, 시·구, 권역, 급지, 세대수, 승인 연도, 대표 거래 면적·가격·계약일을 담는다. `selections`는 저장된 판 번호·선택 단지 ID·선택 시각, `skips`는 건너뛴 판 번호·시각이다. 완료 전 `nextRoundNumber`는 선택 수와 건너뛰기 수를 합친 다음 판 번호이고 완료 뒤에는 `null`이다. JPA 엔티티는 응답에 사용하지 않는다.

선택·건너뛰기 응답에는 `roundResult: {"roundNumber":1,"available":true,"leftPercent":60,"rightPercent":40}`을 추가한다. 비율을 숨기는 경우 `available`은 `false`, 두 퍼센트는 `null`이다. 시작·재조회 응답의 `roundResult`는 `null`이다. 좌우 비율은 **응답 대진의 좌우 순서**에 대응하며 정수로 반올림하고 합계가 항상 100이 되게 한다. `Cache-Control: no-store`를 적용한다.

오류 응답은 `{"code":"GAME_UNAVAILABLE","message":"..."}`처럼 코드와 한국어 메시지를 포함한다. 후보 밖 단지·잘못된 선택은 `400 INVALID_VOTE`, 건너뛰기의 잘못된 판 번호는 `400 INVALID_ROUND`, 순서에 어긋난 판 확정은 `409 VOTE_OUT_OF_ORDER`다. 이미 선택한 판의 변경·건너뛰기는 `409 VOTE_ALREADY_RECORDED`, 건너뛴 판의 선택은 `409 ROUND_ALREADY_SKIPPED`다. 조회할 게임이 없으면 `404 GAME_NOT_FOUND`다.

## 저장과 검증 범위

`V19__create_worldcup_game.sql`은 게임 소유 익명 식별자, 기준 권역 ID, 사용한 급지 범위, 10판 카드 스냅샷, 시작 시각을 저장한다. `V20__create_worldcup_vote.sql`은 게임 완료 시각과 판별 선택 기록을 추가한다. 선택 기록에는 두 후보의 단지 ID, 선택한 단지 ID, 선택 시각을 남긴다. `(game_id, round_number)` 기본 키가 한 판의 중복 기록을 막는다. 단지 가격이나 권역 급지가 나중에 바뀌어도 저장된 대진과 선택은 바뀌지 않는다.

`V22__snapshot_worldcup_vote_zone.sql`은 각 선택에 당시 두 단지의 권역 ID를 추가한다. 기존 선택은 현재 단지 권역으로 한 번 보정하고, 앞으로는 선택을 저장하는 순간의 권역을 함께 기록한다. 집계 조회는 이 저장된 권역을 사용하므로 이후 단지의 권역이 바뀌어도 과거 표의 권역이 바뀌지 않는다. 기존 선택의 단지나 권역을 찾을 수 없다면 마이그레이션에서 실패하므로 해당 기록을 먼저 확인해야 한다.

`V27__create_worldcup_skip.sql`은 건너뛴 판을 별도 테이블에 저장하고 단지 쌍 조회 인덱스를 추가한다. 기존 `worldcup_vote`는 실제 선택만 계속 담는다. 같은 방문자의 판 확정 요청은 DB 잠금 아래 직렬화하고 서비스가 선택과 건너뛰기의 상호 변경을 거부한다. 따라서 건너뛴 판은 급지·순위 집계에도 들어가지 않는다.

집계 입력은 저장된 선택을 선택 시각순으로 한 행씩 읽는다. 완료하지 않은 게임에서 확정한 판도 포함한다. 같은 익명 방문자가 한국 날짜에 같은 단지 쌍을 여러 번 고르면 좌우 순서와 관계없이 첫 표만 유효하다. 권역 간 한 표는 양쪽 권역에서 각각 한 표로 센다. 권역별 유효표 50표, 서로 다른 방문자 10명, 권역 간 유효표 20표를 모두 채워야 투표 기반 급지·순위 전환 대상이 된다. 유효표는 두 단지의 승패에 각각 반영하고, 권역 간 대결은 두 권역의 승패에도 각각 반영한다. 권역 내 대결은 권역 승패에 반영하지 않는다. 점수·급지·순위의 계산식과 표시 단위는 [투표 점수 결정](../history/2026-11-09-worldcup-vote-rating.md)에 기록했다. 초기 급지는 `zone.initial_tier`, 단지의 첫 평가값은 `complex.vote_initial_rating`으로 보존한다. 처음 계산한 단지 평가값을 저장한 뒤에는 주간 대표 가격이 바뀌어도 과거 표를 다시 계산한 결과가 흔들리지 않는다.

대결 비율은 해당 두 단지의 누적 **유효표**만 조회한다. 급지 집계와 동일하게 한국 날짜·익명 방문자·순서 없는 단지 쌍당 첫 표만 세며, 좌우 위치가 뒤집혀도 같은 대결로 본다. 서로 다른 방문자가 10명 미만이면 퍼센트를 공개하지 않는다. 10명 이상이면 선택 직후 저장된 표를 포함해 비율을 계산하며, 건너뛰기에서는 기존 표만 조회한다. 이 비율은 3시간마다 발행하는 권역 급지·순위와 별도로 즉시 계산된다. 같은 방문자의 다른 날짜 표는 각각 유효할 수 있으므로 화면 문구는 `선택 비율`을 사용한다.

## 급지와 권역 내 순위 발행

한국 시간 0·3·6·9·12·15·18·21시에 누적 유효표를 다시 계산한다. 로컬에서는 예약 실행을 기본으로 끄고 운영 프로필에서는 기본으로 켠다. `WORLDCUP_RATING_SCHEDULE_ENABLED`로 켜거나 끌 수 있고 `WORLDCUP_RATING_SCHEDULE_CRON`으로 실행 시각을 조정할 수 있다. 집계는 동일한 DB 읽기 시점에서 초기값과 표를 읽고, 단일 트랜잭션 안에서 첫 평가값·투표 전환 대상 권역의 급지·점수·단지 순위·완료 시각을 반영한다. 발행 행을 잠가 중복 실행을 직렬화하며, 중간에 실패하면 전체 작업이 롤백되어 이전 완료 결과가 남는다.

전환 조건을 채운 권역은 `ranking_basis=VOTE`가 되고 투표 급지와 권역 내 단지 순위를 함께 사용한다. 전환 전 권역은 `ranking_basis=INITIAL`로 남아 배치표 급지와 주간 실거래가 기준 잠정 순위를 사용한다. 주간 실거래 갱신은 대표 가격과 면적을 갱신하지만, `VOTE` 권역의 단지 순위는 다시 쓰지 않는다. 주간 가격 재계산도 집계 발행 행을 먼저 잠가 두 작업이 겹칠 때 순위 쓰기 순서를 보장한다. 집계 완료 시각은 `worldcup_rating_publication.last_completed_at`에 저장한다.

`GET /api/v1/worldcup/zones/{zoneId}/ranking`은 권역 ID·이름·현재 급지·`rankingBasis`(`INITIAL` 또는 `VOTE`)·`lastCompletedAt`·활성 단지의 ID·이름·순위·점수·대표 가격·면적을 반환한다. 첫 집계 전 `lastCompletedAt`은 `null`이다. 한 SQL 조회로 급지와 순위를 함께 읽어 발행 중의 부분 결과를 반환하지 않는다. 없는 권역은 `404 ZONE_NOT_FOUND`다.

대진 생성은 후보 데이터를 한 시점에서 읽고, 저장 직전에 방문자별 잠금을 잡아 미완료 게임을 다시 확인한다. 같은 익명 방문자의 게임 저장·선택·건너뛰기 요청은 트랜잭션 단위로 직렬화한다. 마지막 게임의 저장된 선택·건너뛰기 수로 다음 판을 계산하며, 미완료 게임의 재진입은 새 게임을 만들지 않는다. 별도 출처에서 호출할 프런트엔드의 허용 Origin과 쿠키 전송 정책은 실제 배포 주소가 정해질 때 설정해야 한다.

## 프런트엔드 연동 순서와 출처 설정

1. `GET /api/v1/worldcup/zones`에서 `canStart`를 확인한다. 시작할 수 없는 권역은 `GET /api/v1/worldcup/zones/{zoneId}/alternatives`로 대체 권역을 안내한다.
2. 배너 진입은 `POST /api/v1/worldcup/games`, 권역 진입은 `POST /api/v1/worldcup/zones/{zoneId}/games`를 호출한다. `201`은 새 게임이고 `200`은 기존 미완료 게임이다. 응답의 `matchups`와 `nextRoundNumber`를 사용한다.
3. 재진입 시 `GET /api/v1/worldcup/games/current`를 호출한다. `404 GAME_NOT_FOUND`면 이어갈 게임이 없는 상태다.
4. 현재 판을 선택하거나 건너뛸 때 각각 `PUT /api/v1/worldcup/games/current/rounds/{roundNumber}/vote`, `PUT /api/v1/worldcup/games/current/rounds/{roundNumber}/skip`을 호출한다. 선택 요청은 `Content-Type: application/json`과 `selectedComplexId`가 필요하다. 응답의 `roundResult`는 방금 확정한 판의 선택 비율이며, 다음 판은 `nextRoundNumber`로 결정한다.
5. 10판을 모두 확정하면 `completed=true`, `nextRoundNumber=null`이다. 결과를 다시 조회해도 저장된 선택과 건너뛰기가 유지된다. 권역의 발행된 급지·순위는 `GET /api/v1/worldcup/zones/{zoneId}/ranking`으로 별도 조회한다.

별도 출처의 프런트엔드는 게임 시작·재조회·선택·건너뛰기 요청에 브라우저 `fetch`의 `credentials: "include"`를 설정해야 한다. 첫 시작 응답의 `gjd_uid`는 `HttpOnly` 쿠키라 JavaScript에서 읽지 않는다. 브라우저가 저장해 다음 요청에 자동으로 보내며, URL에 방문자 ID를 넣지 않는다. 로컬에서는 프런트와 백엔드에 모두 `localhost`를 사용한다. `localhost`와 `127.0.0.1`을 섞으면 쿠키 호스트가 달라진다.

`local` 프로필은 기본적으로 `http://localhost:5173`과 `http://localhost:5175`를 월드컵 API의 허용 출처로 둔다. 다른 로컬 포트나 dev·prod 출처는 `WORLDCUP_CORS_ALLOWED_ORIGINS`에 정확한 출처를 쉼표로 구분해 설정한다. 설정하지 않은 dev·prod에는 교차 출처 월드컵 요청을 허용하지 않는다. 이 설정은 `/api/v1/worldcup/**`에만 적용되며, 쿠키 요청에 `*` 출처를 허용하지 않는다. 운영 프런트와 API가 서로 다른 **사이트**에 놓이면 현재 `SameSite=Lax` 쿠키는 교차 사이트 `fetch`에 전송되지 않으므로 도메인 배치 또는 쿠키 정책을 별도로 결정해야 한다.

브라우저에서 확인할 주요 오류는 대진 부족 `409 GAME_UNAVAILABLE`, 하루 시작 한도 `429 DAILY_GAME_LIMIT_REACHED`, 잘못된 판·후보 `400 INVALID_ROUND`/`INVALID_VOTE`, 순서 또는 이미 확정된 판 `409 VOTE_OUT_OF_ORDER`/`VOTE_ALREADY_RECORDED`/`ROUND_ALREADY_SKIPPED`다. 실패 시 응답의 `code`를 사용하고, 화면의 현재 판을 임의로 진행시키지 않는다.
