# 지도 탭 조회 API

관련 이슈: [#154](https://github.com/sugkamoney/geupjido-backend/issues/154), [#157](https://github.com/sugkamoney/geupjido-backend/issues/157)

새 조회 경로는 Swagger의 **지도** 태그 아래에 묶는다. 요청과 응답은 JSON이며 조회 결과에 `Cache-Control: no-store`를 붙인다. 기존 월드컵 게임 생성·선택·건너뛰기 경로는 그대로 사용한다.

| 요청 | 용도 |
|---|---|
| `GET /api/v1/map/cities` | 구·시 라벨, 중심 좌표, 화면 맞춤용 경계 상자와 초기·민심 급지 평균 |
| `GET /api/v1/map/zones?west=&south=&east=&north=` | 화면 경계와 겹치는 권역의 라벨·급지·단지 수·발행된 유효표 수 |
| `GET /api/v1/map/complexes?west=&south=&east=&north=` | 화면 안 단지 핀. 투표 후보 여부와 무관하게 서비스 단지 전부 조회 |
| `GET /api/v1/map/zones/{zoneId}` | 선택한 권역의 GeoJSON 경계와 전체 단지 목록 |
| `GET /api/v1/map/search?q=` | 구 최대 3, 권역 최대 5, 단지 최대 6건 검색 |
| `GET /api/v1/map/status` | 발행 시점의 참여·유효표·전환 권역 수와 다음 예약 집계 시각 |

지도 탭의 화면 요소와 백엔드 연결은 다음과 같다.

| 화면 요소 | 데이터 또는 요청 |
|---|---|
| 구·시 라벨과 마우스 카드 | `/map/cities`의 위치·경계 상자·급지 평균·평균에 포함된 권역 수 |
| 권역 라벨과 마우스 카드 | `/map/zones`의 위치·초기/민심 급지·각 전체 순위와 대상 수 |
| 단지 핀 | `/map/complexes`의 단지 ID·권역 ID·좌표·이름·세대수 |
| 선택한 권역 면·머리글·급지 카드·단지 목록 | `/map/zones/{zoneId}`의 GeoJSON·광역 지역명·구/시 이름·권역 순위·전체 단지 |
| 검색 결과 | `/map/search`의 구·권역·단지 목록. 각 결과에 선택 후 이동할 ID와 위치가 있다 |
| 상태 카드·펼친 칸 | `/map/status`의 참여·유효표·급지별 권역 수·발행·예약 시각 |
| 지도 투표함의 랜덤 시작 | 기존 `POST /api/v1/worldcup/games` |
| 선택한 권역의 단지 비교 | 기존 `GET /api/v1/worldcup/zones`의 `canStart` 확인 후 `POST /api/v1/worldcup/zones/{zoneId}/games`. 시작 불가 권역은 기존 `GET /api/v1/worldcup/zones/{zoneId}/alternatives`로 대체 권역 조회 |

줌 단계에 따른 표시, 경계·라벨·핀의 선택·겹침·애니메이션, 검색창 키보드 조작은 프런트엔드가 위 데이터를 사용해 처리한다. 지도 버튼에서 게임을 시작할 때 기존 미완료 게임이 있으면 새 권역 요청보다 그 게임 이어하기가 우선한다.

좌표는 WGS84 경도·위도(`longitude`, `latitude`)이다. 지도 범위 매개변수도 같은 좌표계이며 `west < east`, `south < north`를 요구한다. 권역 목록에는 중심만 포함하고, 큰 GeoJSON 경계는 권역을 선택할 때 조회한다. 구·시 응답의 `bounds`는 해당 행정구역의 지도 확대 범위다. 검색은 공백과 영문 대소문자를 무시해 이름에 검색어가 포함되는지 확인한다. 없는 권역은 `ZONE_NOT_FOUND` 404, 잘못된 범위·검색어는 `INVALID_REQUEST` 400을 반환한다.

## 급지와 단지 순위

- `initialTier`는 **현재 서비스 DB의 초기 편집 급지**다. 배치표 원문값과 그 밖의 권역에 추정해 배정한 값을 모두 포함한다. 따라서 이 필드를 배치표 1.1의 원본값이라고 표시하면 안 된다.
- `voteTier`는 `rankingBasis=VOTE`가 된 권역에서만 값이 있다. 그 전에는 `null`로 주어 민심 기준 화면에서 `모으는 중`을 표시할 수 있게 한다. `rankingBasis`는 `INITIAL` 또는 `VOTE`다.
- 구·시의 `initialTier`는 해당 권역 초기 급지 평균을 소수 첫째 자리로 내린 값이고, `voteTier`는 투표 급지가 발행된 권역만 평균한 값이다. 투표 급지가 없으면 `null`이다. `initialTierZoneCount`와 `voteTierZoneCount`는 각 평균에 포함된 권역 수다.
- 권역의 `initialRank`/`initialRankTotal`은 초기 급지가 있는 전체 서비스 권역끼리 비교한 순위와 대상 수다. `voteRank`/`voteRankTotal`은 투표 급지가 발행된 권역끼리 비교한 값이다. 해당 급지가 없으면 순위는 `null`이다. 동률은 초기 급지에서 권역 이름·ID, 민심 급지에서 원점수·유효표 수·권역 이름·ID 순으로 풀어 공동 순위를 만들지 않는다. 화면 범위만이 아니라 전체 활성 서비스 권역에서 순위를 계산한다.
- 권역 상세의 `complexes`에는 서비스 범위의 모든 단지를 포함한다. `INITIAL`일 때 `zoneRank`는 대표 거래 가격을 면적으로 보정해 계산한 잠정 순위다. `VOTE`일 때 활성 투표 후보만 투표 순위를 가지며, 비활성 단지의 순위와 점수는 `null`이다. `voteCandidate`가 후보 여부를 나타낸다.
- 권역 상세 단지 목록은 `zoneRank` 오름차순으로 제공한다. 전환 전에는 **㎡당 대표 가격**, 전환 후에는 **투표 점수**로 매긴 순위다. 시안의 단순 거래 총액 내림차순 및 `최근 거래가`라는 문구는 이 API의 순위·가격 의미와 다르다. 화면은 `대표 거래가`와 현재 `rankingBasis`에 맞는 순위 안내를 사용한다. 순위가 없는 단지는 목록 뒤에 남으며, 투표 전환 후 비활성 후보의 순위는 비운다.
- `price`, `representativeArea`, `lastDealDate`는 **선택된 대표 거래**의 값이다. 가장 최근 체결된 거래라는 뜻은 아니므로 화면 문구에서도 `최근 거래가`로 단정하지 않는다.
- 월드컵의 [권역 선택 시작](worldcup-game-api.md)은 같은 권역 대진을 최대 4판 만들고 나머지는 비슷한 급지의 다른 권역과 비교한다. 지도 상세의 버튼은 이 기존 동작을 호출한다.

## 상태 카드의 시점

상태 카드의 `validVoteCount`, `participantCount`와 권역의 `validVoteCount`는 마지막 3시간 집계에서 확정한 유효표 스냅샷이다. 선택 직후의 실시간 표 수가 아니다. 중복 표 제외와 권역 간 표의 양쪽 권역 반영에는 기존 집계 정책을 사용한다. 발행 전에는 0과 `lastCompletedAt=null`이다. `nextScheduledAt`은 이 프로필에서 예약 집계가 켜졌을 때만 반환한다. 개발·운영 서버의 기본 예약은 한국 시간 0·3·6·9·12·15·18·21시다. 로컬 프로필은 기본으로 예약을 실행하지 않는다.

`zoneCount`는 현재 서비스 권역 전체, `initialTierZoneCount`는 초기 급지가 있는 권역, `missingInitialTierZoneCount`는 초기 급지가 없는 권역이다. `voteBasedZoneCount`와 `collectingZoneCount`는 투표 기반 전환 여부로 나눈 수다. `initialGradeCounts`는 초기 급지 1.0~1.9, 2.0~2.9, 3.0~3.9, 4.0~4.9, 5.0~5.9의 다섯 구간별 수다. 이 값들은 DB에서 계산하며 시안의 162·133·80 같은 예시 수치를 사용하지 않는다. 전환 조건은 유효표 50표, 서로 다른 방문자 10명, 권역 간 유효표 20표 모두 충족이다.

선택 기록과 발행된 유효표 수, 다음 예약 시각의 차이는 [월드컵 예약 집계와 지도 상태 Q&A](../learning/backend/q-and-a/worldcup-rating-schedule-and-map-status.md)에 정리했다.

시안의 `배치표 1.1` 고정 날짜와 배치표 원본에만 있는 권역 수는 현재 서비스 DB에 출처·발행일로 저장돼 있지 않다. 현재 응답만으로 이 문구와 날짜를 정확하게 표시할 수 없으므로 프런트 화면에서는 `초기 급지`로 안내하고 날짜를 임의로 채우지 않는다. 원본 배치표를 별도 제품 데이터로 노출하기로 결정하면 그 출처와 날짜를 저장하는 별도 작업이 필요하다.

기존 `app.worldcup-cors.allowed-origins` 설정을 지도 조회 경로에도 적용한다. 프런트엔드가 같은 출처의 프록시를 쓰면 CORS 설정이 필요 없고, 다른 출처에서 직접 호출할 경우 이 허용 목록을 사용한다.
