# 월드컵 게임 시작·대진 재조회 API

- 관련 이슈: [#119](https://github.com/sugkamoney/geupjido-backend/issues/119)
- 대진 선정 기준: [월드컵 10판 대진 생성](worldcup-match-generation.md)
- 카드 필드 기준: [월드컵 카드 정보 계약](worldcup-card-api-contract.md)

## 게임과 익명 방문자

회원가입이나 로그인 없이 서버가 UUID v4 형식의 익명 식별자를 발급한다. 브라우저에는 `gjd_uid` 쿠키로 저장하며, 응답 본문에는 방문자 식별자를 넣지 않는다. 쿠키는 `HttpOnly`, `SameSite=Lax`, `Path=/`, 수명 365일이다. `Secure`는 로컬에서 끄고 dev·prod에서 켠다. dev 환경만 `ANONYMOUS_COOKIE_SECURE`로 조정할 수 있다.

게임별 내부 번호는 `worldcup_game.id`로만 관리한다. 외부 API에는 게임 번호를 노출하지 않는다. 같은 익명 식별자로 게임을 여러 번 시작하면 매번 새 스냅샷을 저장하고, 재조회에서는 가장 나중에 시작한 게임을 반환한다. 이 때문에 브라우저에서 이전 게임을 선택하거나 완료 상태를 구분하는 기능은 아직 없다.

## 요청 흐름

1. `POST /api/v1/worldcup/games`는 배너 기준 권역을 추첨해 게임을 시작한다.
2. `POST /api/v1/worldcup/zones/{zoneId}/games`는 선택한 권역에서 게임을 시작한다.
3. 첫 시작 요청에 유효한 `gjd_uid`가 없으면 서버가 발급하고 `Set-Cookie`로 돌려준다. 기존 쿠키가 있으면 같은 익명 식별자를 사용한다.
4. 두 시작 API는 #116의 대진 생성 결과를 JSONB 스냅샷으로 DB에 저장한 뒤 `201 Created`와 10판을 반환한다.
5. `GET /api/v1/worldcup/games/current`는 쿠키 소유자의 최신 스냅샷을 그대로 반환한다. 없거나 쿠키가 유효하지 않으면 `404 GAME_NOT_FOUND`다.

시작 응답의 `Location`은 `/api/v1/worldcup/games/current`다. 시작과 재조회 응답 모두 `Cache-Control: no-store`를 사용한다. 시작 가능한 대진이 부족하면 `409 GAME_UNAVAILABLE`이며 게임과 새 쿠키를 저장하지 않는다.

## 응답 구조

`baseZoneId`, `tierRangeUsed`, `matchups`를 반환한다. `matchups`는 표시 순서대로 10개이며 각 항목의 `roundNumber`는 1부터 10까지다. `type`은 `CROSS_ZONE` 또는 `WITHIN_ZONE`이고 `left`와 `right`는 #113 카드 계약의 단지 ID, 이름, 시·구, 권역, 급지, 세대수, 승인 연도, 대표 거래 면적·가격·계약일을 담는다. JPA 엔티티는 응답에 사용하지 않는다.

오류 응답은 `{"code":"GAME_UNAVAILABLE","message":"..."}`처럼 코드와 한국어 메시지를 포함한다. 프로젝트의 첫 사용자용 API이므로 이 형식을 공통 오류 응답의 출발점으로 둔다.

## 저장과 검증 범위

`V19__create_worldcup_game.sql`은 게임 소유 익명 식별자, 기준 권역 ID, 사용한 급지 범위, 10판 카드 스냅샷, 시작 시각을 저장한다. 단지 가격이나 권역 급지가 나중에 바뀌어도 재조회한 카드 값과 순서는 바뀌지 않는다. 소유 식별자와 내부 번호 순서의 인덱스로 최신 게임을 찾는다.

이번 구현은 투표 선택과 완료 상태, 하루 6게임 한도, 중단한 판의 진행 위치를 저장하지 않는다. 따라서 `current`는 정확히는 **가장 최근 시작한 게임**이다. 별도 출처에서 호출할 프런트엔드의 허용 Origin과 쿠키 전송 정책은 실제 배포 주소가 정해질 때 설정해야 한다.
