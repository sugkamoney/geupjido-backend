# 월드컵 타임라인 API

관련 이슈: [#160](https://github.com/sugkamoney/geupjido-backend/issues/160)

`GET /api/v1/worldcup/timeline`은 저장된 월드컵 **선택**을 최신순으로 최대 20건 반환한다. 쿠키 없이도 조회할 수 있다. `zoneId`를 주면 그 권역이 대결의 왼쪽 또는 오른쪽에 등장한 판만 반환한다. 현재 선택된 지도 권역을 `zoneId`로 보내면 시안의 `전체 / 성수 대진` 필터가 된다. 일치하는 판이 없으면 `items: []`다. `zoneId`를 비워 보내면 전체 목록을 조회한다.

```json
{
  "items": [
    {
      "displayName": "익명의 벚나무",
      "mine": false,
      "selectedAt": "2026-11-14T04:00:00Z",
      "left": {
        "complexId": "A12345678",
        "name": "왼쪽 단지",
        "zoneId": "zone-left",
        "zoneName": "성수"
      },
      "right": {
        "complexId": "A87654321",
        "name": "오른쪽 단지",
        "zoneId": "zone-right",
        "zoneName": "가락"
      },
      "selectedComplexId": "A12345678"
    }
  ]
}
```

`selectedComplexId`가 `left.complexId` 또는 `right.complexId`와 같으면 그쪽에 `고름` 표시를 한다. 양쪽 권역 ID가 같으면 같은 권역의 단지 비교다. 권역 이름을 누르면 해당 `zoneId`로 지도 권역 상세를 열 수 있다. `selectedAt`은 절대 시각이며, `방금`·`1분 전` 같은 상대 시각은 브라우저에서 계산한다.

유효한 `gjd_uid` 쿠키가 있으면 자신의 표는 `mine=true`, `displayName=나`가 된다. 다른 방문자는 쿠키 UUID를 16개 나무 이름 중 하나로 바꿔 `익명의 ○○`으로 표시한다. 이름은 고유 식별자가 아니고 서로 다른 방문자가 같은 이름을 가질 수 있다. 응답에 쿠키 값, 익명 방문자 ID, 내부 게임 ID와 판 번호는 넣지 않는다. 조회 요청은 쿠키를 새로 만들지 않는다.

건너뛴 판은 선택 기록이 없어 목록에 들어가지 않는다. 이 API는 저장된 선택을 보여주며, 3시간 급지 집계에서 중복 표 등을 제외한 **유효표 수와 동일한 목록은 아니다**. 대결별 선택 비율이나 집계 점수는 제공하지 않는다. 응답은 `Cache-Control: no-store`이며, 월드컵 API의 기존 CORS 설정을 적용한다. 시안처럼 전체 선택이 5건 미만일 때 타임라인 영역을 숨기려면 **전체 조회**의 `items.length`로 판단한다. 권역별 조회 결과만 비었을 때는 해당 권역의 빈 목록 안내를 보여줄 수 있다.
