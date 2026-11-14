# 지도 탭 조회 API

관련 이슈: [#154](https://github.com/sugkamoney/geupjido-backend/issues/154)

새 조회 경로는 Swagger의 **지도** 태그 아래에 묶는다. 요청과 응답은 JSON이며 조회 결과에 `Cache-Control: no-store`를 붙인다. 기존 월드컵 게임 생성·선택·건너뛰기 경로는 그대로 사용한다.

| 요청 | 용도 |
|---|---|
| `GET /api/v1/map/cities` | 구·시 라벨, 중심 좌표, 화면 맞춤용 경계 상자와 초기·민심 급지 평균 |
| `GET /api/v1/map/zones?west=&south=&east=&north=` | 화면 경계와 겹치는 권역의 라벨·급지·단지 수·발행된 유효표 수 |
| `GET /api/v1/map/complexes?west=&south=&east=&north=` | 화면 안 단지 핀. 투표 후보 여부와 무관하게 서비스 단지 전부 조회 |
| `GET /api/v1/map/zones/{zoneId}` | 선택한 권역의 GeoJSON 경계와 전체 단지 목록 |
| `GET /api/v1/map/search?q=` | 구 최대 3, 권역 최대 5, 단지 최대 6건 검색 |
| `GET /api/v1/map/status` | 발행 시점의 참여·유효표·전환 권역 수와 다음 예약 집계 시각 |

좌표는 WGS84 경도·위도(`longitude`, `latitude`)이다. 지도 범위 매개변수도 같은 좌표계이며 `west < east`, `south < north`를 요구한다. 권역 목록에는 중심만 포함하고, 큰 GeoJSON 경계는 권역을 선택할 때 조회한다. 구·시 응답의 `bounds`는 해당 행정구역의 지도 확대 범위다. 검색은 공백과 영문 대소문자를 무시해 이름에 검색어가 포함되는지 확인한다. 없는 권역은 `ZONE_NOT_FOUND` 404, 잘못된 범위·검색어는 `INVALID_REQUEST` 400을 반환한다.

## 급지와 단지 순위

- `initialTier`는 **현재 서비스 DB의 초기 편집 급지**다. 배치표 원문값과 그 밖의 권역에 추정해 배정한 값을 모두 포함한다. 따라서 이 필드를 배치표 1.1의 원본값이라고 표시하면 안 된다.
- `voteTier`는 `rankingBasis=VOTE`가 된 권역에서만 값이 있다. 그 전에는 `null`로 주어 민심 기준 화면에서 `모으는 중`을 표시할 수 있게 한다. `rankingBasis`는 `INITIAL` 또는 `VOTE`다.
- 구·시의 `initialTier`는 해당 권역 초기 급지 평균을 소수 첫째 자리로 내린 값이고, `voteTier`는 투표 급지가 발행된 권역만 평균한 값이다. 투표 급지가 없으면 `null`이다.
- 권역 상세의 `complexes`에는 서비스 범위의 모든 단지를 포함한다. `INITIAL`일 때 `zoneRank`는 대표 거래 가격을 면적으로 보정해 계산한 잠정 순위다. `VOTE`일 때 활성 투표 후보만 투표 순위를 가지며, 비활성 단지의 순위와 점수는 `null`이다. `voteCandidate`가 후보 여부를 나타낸다.
- `price`, `representativeArea`, `lastDealDate`는 **선택된 대표 거래**의 값이다. 가장 최근 체결된 거래라는 뜻은 아니므로 화면 문구에서도 `최근 거래가`로 단정하지 않는다.
- 월드컵의 [권역 선택 시작](worldcup-game-api.md)은 같은 권역 대진을 최대 4판 만들고 나머지는 비슷한 급지의 다른 권역과 비교한다. 지도 상세의 버튼은 이 기존 동작을 호출한다.

## 상태 카드의 시점

상태 카드의 `validVoteCount`, `participantCount`와 권역의 `validVoteCount`는 마지막 3시간 집계에서 확정한 유효표 스냅샷이다. 선택 직후의 실시간 표 수가 아니다. 중복 표 제외와 권역 간 표의 양쪽 권역 반영에는 기존 집계 정책을 사용한다. 발행 전에는 0과 `lastCompletedAt=null`이다. `nextScheduledAt`은 이 프로필에서 예약 집계가 켜졌을 때만 반환한다. 운영 기본 예약은 한국 시간 0·3·6·9·12·15·18·21시다.

기존 `app.worldcup-cors.allowed-origins` 설정을 지도 조회 경로에도 적용한다. 프런트엔드가 같은 출처의 프록시를 쓰면 CORS 설정이 필요 없고, 다른 출처에서 직접 호출할 경우 이 허용 목록을 사용한다.
