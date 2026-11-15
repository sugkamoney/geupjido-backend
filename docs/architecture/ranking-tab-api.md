# 랭킹 탭 API 계약

관련 이슈: [#178](https://github.com/sugkamoney/geupjido-backend/issues/178), [랭킹 순위 결정](../history/2026-11-15-ranking-tab-policy.md)

## 조회

`GET /api/v1/rankings/zones?region=ALL&tab=GRADE_1&sort=CURRENT`

| 요청 값 | 허용 값 | 기본값 | 뜻 |
|---|---|---|---|
| `region` | `ALL`, `SEOUL`, `GYEONGGI_INCHEON` | `ALL` | 전체 수도권 / 서울 / 경기·인천 순위 범위 |
| `tab` | `GRADE_1`~`GRADE_5`, `COLLECTING` | `GRADE_1` | 현재 급지 구간 또는 모으는 중 상태 |
| `sort` | `CURRENT`, `RISEN`, `FALLEN` | `CURRENT` | 현재 순위 / 상승 폭 / 하락 폭 |

잘못된 요청 값은 공통 `INVALID_REQUEST` 400 오류 응답을 반환한다. 응답은 캐시하지 않는다. 랭킹 탭은 지도 조회 경로와 별개 Swagger **랭킹** 태그로 노출한다.

## 응답에 필요한 값

| 위치 | 필드 | 의미 |
|---|---|---|
| 상단 | `region`, `tab`, `sort` | 실제 적용한 필터 |
| 상단 | `totalZoneCount`, `rankedZoneCount` | 선택 지역의 전체 활성 권역 수와 현재 급지가 있어 순위를 받은 권역 수 |
| 상단 | `gradeCounts` | 선택 지역의 현재 1~5급지별 권역 수 |
| 상단 | `collectingCount` | 선택 지역의 `INITIAL` 권역 수. 급지 탭 개수와 겹칠 수 있음 |
| 상단 | `lastCompletedAt`, `previousCompletedAt` | 현재와 직전 성공 발행 시각. 최초 발행 전과 직전 결과가 없을 때는 `null` |
| 각 권역 | `zoneId`, `name`, `cityName`, `regionName` | 권역 선택과 지역 표시에 필요한 값 |
| 각 권역 | `rankingBasis`, `tier`, `rank`, `rankTotal` | 초기/민심 반영 상태, 현재 적용 급지, 선택 지역 범위의 순위와 분모 |
| 각 권역 | `previousTier`, `tierDelta`, `movement` | 직전 성공 발행의 현재 급지, 급지 차이의 절댓값, `UP`/`DOWN`/`SAME`/`NONE` |
| 각 권역 | `validVoteCount`, `participantCount`, `crossZoneVoteCount` | 마지막 발행의 권역별 전환 조건 진행 수치 |

`rankTotal`은 급지가 있는 선택 지역 권역 수다. 초기 급지가 없어 순위를 매길 수 없는 권역은 `rank=null`, `tier=null`이고 `COLLECTING` 탭에 포함한다. `tierDelta`와 `previousTier`는 비교할 발행 결과가 없으면 `null`이다. `movement=NONE`은 아직 비교 결과가 없는 상태다.

`CURRENT`는 `rank` 오름차순, `RISEN`과 `FALLEN`은 해당 방향으로 변한 권역만 변동 폭 내림차순·현재 지역 순위 오름차순으로 정렬한다. `COLLECTING` 탭에서는 상승·하락 칩을 숨기고 유효표가 많은 순으로 보여준다. 탭 필터나 정렬을 적용해도 권역의 `rank`와 `rankTotal`은 바뀌지 않는다. 빈 결과는 빈 목록으로 반환한다.

권역 행을 선택한 뒤의 경계와 단지 목록은 기존 `GET /api/v1/map/zones/{zoneId}`를 사용한다. 지도 API의 `initialRank`·`voteRank`는 기존 의미를 유지한다.
