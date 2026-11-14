# 개발 API 프런트엔드 연동

## 접속 주소와 현재 경계

- 기본 주소: `https://dev-api.geupjido.site`
- API 문서: `https://dev-api.geupjido.site/swagger-ui/index.html`
- OpenAPI JSON: `https://dev-api.geupjido.site/v3/api-docs`
- 헬스 체크: `https://dev-api.geupjido.site/actuator/health`

프런트엔드 저장소의 현재 `vite.config.ts`는 `/api`를 로컬 백엔드로 전달하고, 월드컵 화면은 아직 fixture 기반이다. 프런트엔드 담당자가 실제 API 연결을 맡는다. 백엔드 저장소에서는 프런트엔드 코드를 수정하지 않는다.

## 로컬 프런트엔드에서 호출

Vite의 `/api` 프록시 대상만 개발 API의 HTTPS 주소로 바꾸고 경로는 그대로 전달한다. 화면에서는 절대 URL 대신 상대 경로를 호출한다. 쿠키가 필요한 요청에는 `credentials: 'include'`를 사용한다.

```ts
// 프런트엔드 담당자에게 전달할 설정 예시
proxy: {
  '/api': { target: 'https://dev-api.geupjido.site', changeOrigin: true },
}

fetch('/api/v1/worldcup/games', {
  method: 'POST',
  credentials: 'include',
});
```

서버는 최초 게임 시작 응답에서 `gjd_uid` 쿠키를 발급한다. 이 쿠키는 `HttpOnly`, `Secure`, `SameSite=Lax`, 경로 `/`이며 JavaScript에서 값을 읽지 않는다. 브라우저가 후속 투표·이어하기 요청에 자동으로 첨부한다. 로컬 페이지에서 API의 절대 HTTPS URL을 직접 `fetch`하면 서로 다른 사이트 간 요청이 되므로 `SameSite=Lax` 쿠키가 후속 `fetch`에 실리지 않는다. Vite 프록시로 브라우저 요청을 같은 출처의 `/api`에 보낸다.

Safari는 HTTP `localhost`에 대한 `Secure` 쿠키 예외를 지원하지 않는다. Safari까지 로컬에서 검증할 때는 신뢰할 수 있는 로컬 HTTPS Vite 주소를 사용하거나, HTTPS로 배포된 동일 사이트의 프런트엔드를 사용한다. 이 조건은 [MDN 쿠키 안내](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Cookies)에 따른다.

백엔드 CORS는 개발 프런트의 명시적 출처만 허용하고 자격 증명 요청을 허용한다. 새로운 배포 프런트엔드 주소가 확정되면 서버의 허용 출처 설정에 추가한 뒤 사전 요청과 실제 브라우저 쿠키 동작을 검증한다. 와일드카드 출처는 사용하지 않는다.

## 월드컵 요청 순서

| 순서 | 메서드와 경로 | 용도 |
|---|---|---|
| 1 | `GET /api/v1/worldcup/zones` | 시작 가능한 권역과 `zoneId`, `canStart` 조회 |
| 2 | `POST /api/v1/worldcup/games` | 배너에서 임의 권역 게임 시작 |
| 2a | `POST /api/v1/worldcup/zones/{zoneId}/games` | 선택한 권역의 게임 시작 |
| 3 | `PUT /api/v1/worldcup/games/current/rounds/{roundNumber}/vote` | 현재 판의 단지 선택 저장 |
| 3a | `PUT /api/v1/worldcup/games/current/rounds/{roundNumber}/skip` | 현재 판 건너뛰기 저장 |
| 4 | `GET /api/v1/worldcup/games/current` | 같은 쿠키의 진행 중 게임 재조회 |
| 5 | `GET /api/v1/worldcup/timeline?zoneId={zoneId}` | 최근 선택 20건 조회. `zoneId`를 생략하면 전체 대진 |

선택 요청 본문은 `{"selectedComplexId":"<현재 판의 left 또는 right complexId>"}`다. 시작 응답은 새 게임이면 201, 기존 게임을 이어가면 200이다. 게임 응답의 `matchups`는 고정된 10판이며, `nextRoundNumber`, `selections`, `skips`, `completed`로 진행 상태를 표시한다. 선택·건너뛰기 응답의 `roundResult`에 공개 가능한 좌우 선택 비율이 포함된다. 응답 DTO의 전체 필드는 OpenAPI JSON을 기준으로 확인한다.

타임라인은 공개 조회라 쿠키 없이도 호출할 수 있다. 같은 브라우저가 `gjd_uid` 쿠키를 보내면 본인 표에 `mine=true`, `displayName=나`를 돌려준다. 양쪽 단지·권역, 선택 단지 ID, 선택 시각을 사용해 시안의 대결 줄을 그린다. 상세 계약은 [월드컵 타임라인 API](worldcup-timeline-api.md)를 참조한다. 지도 권역의 전체 단지 순위는 `GET /api/v1/map/zones/{zoneId}`를 사용한다.

오류 응답은 `code`, `message`를 사용한다. 주요 상태는 잘못된 선택 400, 이어갈 게임 없음 404, 이미 선택한 판 또는 순서 충돌 409, 하루 시작 한도 429다. UI는 409를 새 투표 성공으로 처리하지 않고 현재 게임을 다시 조회해야 한다.

## 확인한 범위

2026-11-13 개발 서버에서 허용된 로컬 출처의 CORS 사전 요청은 200과 `Access-Control-Allow-Credentials: true`를 반환했고, 허용하지 않은 출처는 403이었다. 익명 쿠키로 게임 시작 201, 첫 판 선택 200, 같은 쿠키로 이어하기 200을 확인했다. 선택은 한 건 저장되고 다음 판 번호는 2였다. 실제 프런트엔드 화면의 브라우저 쿠키 동작은 프런트엔드 API 연결 후 별도로 확인해야 한다.

같은 날 Chrome 브라우저 엔진에서도 로컬 테스트 화면을 임시 `/api` 프록시로 개발 API에 연결해 확인했다. 새 브라우저 프로필에서 게임 생성·첫 판 투표·재조회가 PASS였고, 같은 프로필로 페이지를 다시 열어도 선택 1건과 다음 판 번호 2가 유지됐다. `HttpOnly` 방문자 쿠키는 JavaScript에서 보이지 않았다. 프록시 없이 허용하지 않은 로컬 출처에서 개발 API를 직접 호출한 검사는 브라우저의 `Failed to fetch`로 차단되어 PASS였다. 이는 백엔드 연결 규약 검증이며, 프런트엔드 담당자의 실제 화면 연결 후에는 그 화면에서 다시 확인한다.
