# 월드컵 권역 선택 API

- 관련 이슈: [#139](https://github.com/sugkamoney/geupjido-backend/issues/139)
- 게임 시작과 카드 응답: [월드컵 게임 API](worldcup-game-api.md)

## 권역 선택 목록

`GET /api/v1/worldcup/zones`는 서비스에 등록된 권역을 시·구명, 권역명, ID 순서로 반환한다. 각 항목에는 `zoneId`, `cityName`, `zoneName`, 현재 `tier`, `activeComplexCount`, `canStart`가 들어간다. 활성 단지가 없는 권역도 목록에 포함된다. 급지가 없는 권역의 `tier`는 `null`이며 `canStart`는 `false`다.

`canStart`는 해당 권역에 활성 단지가 **5곳 이상** 있어 서로 다른 단지 쌍 10개를 만들 수 있을 때만 `true`다. 선택한 권역의 단지 비교는 다른 권역 단지를 섞지 않는다. 일반 월드컵의 4+6 대진 규칙과 별개다. 후보가 4곳 이하면 인접 대체 권역을 안내한다.

## 인접 대체 권역

`GET /api/v1/worldcup/zones/{zoneId}/alternatives`는 선택한 권역에서 현재 10판을 만들 수 없을 때 `zone_adjacency`에 등록된 인접 권역 중 `canStart=true`인 권역을 반환한다. 시작 가능한 권역이거나 적합한 인접 권역이 없으면 빈 배열이다. 존재하지 않는 권역 ID는 `404 ZONE_NOT_FOUND`다. 목록 항목은 권역 선택 API와 같은 구조다.

두 조회는 게임·투표를 저장하지 않고 일일 시작 한도를 사용하지 않는다. 응답에는 `Cache-Control: no-store`를 적용한다. 사용자가 권역을 고른 뒤에는 `POST /api/v1/worldcup/zones/{zoneId}/games`로 권역 안 단지 비교를 시작한다. 조회 뒤 단지 활성 상태가 바뀔 수 있으므로, 최종 시작 가능 여부는 게임 시작 응답이 결정한다.
