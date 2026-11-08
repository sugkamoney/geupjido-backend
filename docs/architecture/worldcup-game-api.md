# 월드컵 게임 시작·투표 선택·이어하기 API

- 관련 이슈: [#119](https://github.com/sugkamoney/geupjido-backend/issues/119), [#122](https://github.com/sugkamoney/geupjido-backend/issues/122)
- 대진 선정 기준: [월드컵 10판 대진 생성](worldcup-match-generation.md)
- 카드 필드 기준: [월드컵 카드 정보 계약](worldcup-card-api-contract.md)

## 게임과 익명 방문자

회원가입이나 로그인 없이 서버가 UUID v4 형식의 익명 식별자를 발급한다. 브라우저에는 `gjd_uid` 쿠키로 저장하며, 응답 본문에는 방문자 식별자를 넣지 않는다. 쿠키는 `HttpOnly`, `SameSite=Lax`, `Path=/`, 수명 365일이다. `Secure`는 로컬에서 끄고 dev·prod에서 켠다. dev 환경만 `ANONYMOUS_COOKIE_SECURE`로 조정할 수 있다.

게임별 내부 번호는 `worldcup_game.id`로만 관리한다. 외부 API에는 게임 번호를 노출하지 않는다. 같은 익명 식별자에게 미완료 게임이 있으면 새 시작 요청에서도 그 게임을 반환한다. 10판을 모두 선택해 완료한 뒤에는 새 스냅샷을 만들 수 있다. 재조회와 투표 선택은 가장 최근 게임을 대상으로 한다.

## 요청 흐름

1. `POST /api/v1/worldcup/games`는 배너 기준 권역을 추첨해 게임을 시작한다.
2. `POST /api/v1/worldcup/zones/{zoneId}/games`는 선택한 권역에서 게임을 시작한다.
3. 첫 시작 요청에 유효한 `gjd_uid`가 없으면 서버가 발급하고 `Set-Cookie`로 돌려준다. 기존 쿠키가 있으면 같은 익명 식별자를 사용한다.
4. 두 시작 API는 미완료 게임이 없을 때만 #116의 대진 생성 결과를 JSONB 스냅샷으로 DB에 저장하고 `201 Created`와 10판을 반환한다. 미완료 게임이 있으면 새 대진을 만들지 않고 `200 OK`로 저장된 진행 상태를 반환한다. 선택한 권역 진입에서도 기존 미완료 게임이 우선한다.
5. `GET /api/v1/worldcup/games/current`는 쿠키 소유자의 최신 대진·선택·진행 상태를 반환한다. 없거나 쿠키가 유효하지 않으면 `404 GAME_NOT_FOUND`다.
6. `PUT /api/v1/worldcup/games/current/rounds/{roundNumber}/vote`의 JSON 본문 `{"selectedComplexId":"단지ID"}`로 다음 판을 선택한다. 서버는 저장된 대진의 두 단지 중 하나인지 검증하고 1번부터 순서대로 저장한다. 같은 선택 재요청은 기존 결과를 반환하고 표를 추가하지 않는다. 이미 고른 판을 다른 단지로 바꿀 수 없다.
7. 10번 판의 선택까지 저장되면 게임을 완료한다. 다시 조회하면 10개 선택과 완료 상태가 유지된다.

## 하루 게임 시작 한도

익명 방문자별로 한국 시간(`Asia/Seoul`) 날짜에 새로 생성한 게임을 최대 6개까지 허용한다. `worldcup_game.started_at`이 해당 날짜의 00:00 이상, 다음 날짜 00:00 미만인 행을 센다. 완료 여부와 관계없이 시작한 게임은 1개로 계산하며, 진행 중인 게임을 이어가기 위해 시작 요청을 반복해도 추가로 세지 않는다. 한도에 도달하면 새 대진을 생성하지 않고 `429 DAILY_GAME_LIMIT_REACHED`를 반환한다. 한국 시간 자정부터 새 날짜의 한도를 적용한다.

실제 저장 직전에 익명 방문자별 DB 잠금을 잡고 한도를 다시 확인하므로 동시 요청으로 6개를 초과해 저장하지 않는다. 잠금 뒤에 확인한 시각을 게임 시작 시각으로 함께 저장해 자정 경계에서도 같은 날짜에 집계한다. 브라우저 쿠키를 지우거나 바꾸는 행위까지 막는 사용자 계정 기반 제한은 아니다.

새 게임 생성 응답(`201`)의 `Location`은 `/api/v1/worldcup/games/current`다. 시작과 재조회 응답 모두 `Cache-Control: no-store`를 사용한다. 시작 가능한 대진이 부족하면 `409 GAME_UNAVAILABLE`이며 게임과 새 쿠키를 저장하지 않는다.

## 응답 구조

`baseZoneId`, `tierRangeUsed`, `matchups`, `selections`, `nextRoundNumber`, `completed`를 반환한다. `matchups`는 표시 순서대로 10개이며 각 항목의 `roundNumber`는 1부터 10까지다. `type`은 `CROSS_ZONE` 또는 `WITHIN_ZONE`이고 `left`와 `right`는 #113 카드 계약의 단지 ID, 이름, 시·구, 권역, 급지, 세대수, 승인 연도, 대표 거래 면적·가격·계약일을 담는다. `selections`는 저장된 판 번호·선택 단지 ID·선택 시각이다. 완료 전 `nextRoundNumber`는 다음 판 번호이고 완료 뒤에는 `null`이다. JPA 엔티티는 응답에 사용하지 않는다.

오류 응답은 `{"code":"GAME_UNAVAILABLE","message":"..."}`처럼 코드와 한국어 메시지를 포함한다. 후보 밖 단지·잘못된 판은 `400 INVALID_VOTE`, 순서 건너뛰기는 `409 VOTE_OUT_OF_ORDER`, 확정한 선택 변경은 `409 VOTE_ALREADY_RECORDED`다. 조회할 게임이 없으면 `404 GAME_NOT_FOUND`다.

## 저장과 검증 범위

`V19__create_worldcup_game.sql`은 게임 소유 익명 식별자, 기준 권역 ID, 사용한 급지 범위, 10판 카드 스냅샷, 시작 시각을 저장한다. `V20__create_worldcup_vote.sql`은 게임 완료 시각과 판별 선택 기록을 추가한다. 선택 기록에는 두 후보의 단지 ID, 선택한 단지 ID, 선택 시각을 남긴다. `(game_id, round_number)` 기본 키가 한 판의 중복 기록을 막는다. 단지 가격이나 권역 급지가 나중에 바뀌어도 저장된 대진과 선택은 바뀌지 않는다.

대진 생성은 후보 데이터를 한 시점에서 읽고, 저장 직전에 방문자별 잠금을 잡아 미완료 게임을 다시 확인한다. 같은 익명 방문자의 게임 저장과 투표 선택 요청은 트랜잭션 단위로 직렬화한다. 마지막 게임의 저장된 선택 수로 다음 판을 계산하며, 미완료 게임의 재진입은 새 게임을 만들지 않는다. 공개 비율과 점수·급지 집계는 후속 작업이다. 별도 출처에서 호출할 프런트엔드의 허용 Origin과 쿠키 전송 정책은 실제 배포 주소가 정해질 때 설정해야 한다.
