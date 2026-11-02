# 로컬 권역 접경 관계 생성

2026-11-02 이슈 #110 범위에서 `zone.polygon`의 실제 경계가 맞닿는 권역 쌍만 `zone_adjacency`의 `auto_touch`로 생성했다. `ST_Touches`를 쓰므로 면이 겹치는 권역은 자동 접경 관계에 포함하지 않는다. 인접은 이후 투표 대진에서 우선순위 신호이며 필수 조건은 아니다.

## 실행 경로

`ZoneAdjacencyAutoTouchService.refreshTouchingZoneAdjacencies()`는 한 트랜잭션에서 경계가 더 이상 맞닿지 않는 기존 `auto_touch`만 지우고 현재 접경 후보를 양방향으로 저장한다. 중복 키는 건너뛰며 `manual`과 `auto_city` 관계는 덮어쓰거나 지우지 않는다.

로컬 프로필의 수동 Runner는 기본적으로 꺼져 있다. 아래처럼 명시적으로 활성화해 실행했다. 이 Runner는 `local` 프로필에서만 등록된다.

```sh
ZONE_ADJACENCY_AUTO_TOUCH_ENABLED=true ./gradlew bootRun --args='--spring.profiles.active=local --spring.main.web-application-type=none'
```

## 검증 결과

- 실행 전 로컬 `zone_adjacency`는 0행, 현재 `ST_Touches` 후보는 방향 없는 쌍 375개였다.
- 실행 후 `auto_touch` 750행, 방향 없는 쌍 375개다.
- 자기 연결 0건, 역방향 누락 0건, 실제로 맞닿지 않는 `auto_touch` 0건이다.
- 로컬 PostgreSQL·PostGIS 통합 테스트 2건과 `./gradlew clean build`가 성공했다.

이 결과는 로컬 DB에만 반영했다. 다각형이 겹치거나 떨어진 권역에는 자동 관계를 만들지 않았으며, 대체 생활권 수동 관계와 후보 부족 시 거리·급지 범위를 넓히는 대진 규칙은 후속 작업이다. 운영 DB에는 실행하지 않았다.
