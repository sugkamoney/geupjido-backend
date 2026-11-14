# 월드컵 선택은 보이는데 지도 유효표가 0인 이유

관련 이슈: [#172](https://github.com/sugkamoney/geupjido-backend/issues/172)

## Q. 월드컵 타임라인에 선택이 있는데 지도 상태의 유효표 수는 왜 0일 수 있나?

두 API가 읽는 데이터와 시점이 다르다. `GET /api/v1/worldcup/timeline`은 저장된 **선택 기록**을 최신순으로 읽는다. 건너뛴 판은 선택 기록이 없어서 나오지 않는다. `GET /api/v1/map/status`의 `validVoteCount`와 `participantCount`는 마지막 급지·순위 발행 때 저장한 **유효표 스냅샷**을 읽는다. 지도 API 요청마다 원본 선택을 새로 집계하지 않는다.

발행 전에는 타임라인에 선택이 있어도 상태의 두 수가 0이고 `lastCompletedAt`이 `null`일 수 있다. 발행 후에도 방문자가 같은 날 같은 단지 쌍에 반복한 선택은 유효표에서 제외되므로 타임라인 건수와 유효표 수를 같다고 가정하면 안 된다. 권역이 투표 급지로 전환되려면 그 권역에 유효표 50표, 서로 다른 방문자 10명, 권역 간 유효표 20표가 모두 필요하다.

프로젝트 적용: [타임라인 API](../../../architecture/worldcup-timeline-api.md), [지도 상태 조회 SQL](../../../../src/main/java/com/geupjido/map/repository/MapQueryRepository.java), [유효표 정책](../../../../src/main/java/com/geupjido/match/service/WorldcupVoteEligibilityPolicy.java)을 함께 보면 원본 선택·유효표·발행 결과의 차이를 확인할 수 있다.

## Q. 3시간 집계 로직이 있는데 개발 서버에서 실행되지 않았던 이유는?

예약 실행을 맡는 `WorldcupRatingScheduler`는 `app.worldcup-rating.schedule-enabled=true`일 때만 생성된다. `prod` 프로필에는 이 값의 기본값이 `true`, `local` 프로필에는 `false`다. 점검 당시 `dev` 프로필에는 설정이 없어 예약 실행이 꺼져 있었다. `dev` 설정에 기본값 `true`를 추가했고, 필요하면 `WORLDCUP_RATING_SCHEDULE_ENABLED=false`로 중지할 수 있다. 집계 계산·발행 로직 자체를 새로 만든 것은 아니다.

예약 시각은 `0 0 */3 * * *`이라는 Spring의 **초·분·시·일·월·요일** 순서 Cron 표현식과 `Asia/Seoul` 시간대로 정해진다. 한국 시간 매일 0·3·6·9·12·15·18·21시에 실행한다. 앱을 시작하자마자 이전 실행분을 소급해 수행하는 설정은 없다. `local` 프로필은 테스트 중 원치 않는 예약 쓰기를 막기 위해 기본으로 꺼 둔다.

프로젝트 적용: [예약 실행 코드](../../../../src/main/java/com/geupjido/match/runner/WorldcupRatingScheduler.java), [개발 설정](../../../../src/main/resources/application-dev.yml), [로컬 설정](../../../../src/main/resources/application-local.yml)을 대조한다.

## Q. `nextScheduledAt`과 `lastCompletedAt`은 각각 무엇을 증명하나?

`nextScheduledAt`은 지도 서비스가 **현재 프로필의 예약 설정과 Cron**으로 계산한 다음 예정 시각이다. 예약이 꺼져 있으면 `null`이다. 응답 시각은 UTC 절대 시각으로 직렬화될 수 있으므로 한국 시간과 비교할 때 시차를 적용한다. 이 값이 있다고 해서 예약 작업이 실제로 성공한 것은 아니다.

`lastCompletedAt`은 집계 발행 트랜잭션이 완료 시각을 DB에 기록한 결과다. 아직 발행이 없으면 `null`이고, 성공한 발행 뒤에 갱신된다. 발행은 행 잠금과 단일 트랜잭션을 사용하므로 중간에 실패하면 이전 발행 결과가 유지된다. 따라서 **예약 활성화 여부는 `nextScheduledAt`**, **마지막 발행 성공 여부는 `lastCompletedAt`과 서비스 로그**로 따로 확인한다. 표가 적어 급지 전환 조건을 채우지 못해도 발행이 성공하면 완료 시각은 갱신될 수 있다.

프로젝트 적용: [지도 상태 서비스](../../../../src/main/java/com/geupjido/map/service/MapQueryService.java)는 다음 시각을 계산하고, [발행 서비스](../../../../src/main/java/com/geupjido/match/service/WorldcupVoteRatingPublicationService.java)는 발행 완료 시각을 기록한다.

## Q. 배포 후에는 어떤 순서로 확인하나?

1. `main` 병합으로 시작된 GitHub Actions의 빌드·개발 배포 성공을 확인한다.
2. 개발 API의 `GET /api/v1/map/status`에서 `nextScheduledAt`이 다음 한국 시간 3시간 경계를 가리키는지 확인한다. 이 단계에서는 `lastCompletedAt=null`이어도 정상일 수 있다.
3. 첫 예정 시각이 지난 뒤 같은 API의 `lastCompletedAt`이 갱신됐는지 확인한다. 갱신되지 않았다면 개발 서버의 `geupjido-dev.service` 로그에서 예약 작업의 실패를 확인한다.
4. `validVoteCount`와 타임라인 항목 수를 직접 비교해 성공 여부를 판단하지 않는다. 유효성 필터와 발행 시점이 다르기 때문이다.

이번 수정은 로컬 PostgreSQL에 연결한 `dev` 프로필에서 앱이 시작되고 `nextScheduledAt`이 표시되는 것까지 검증했다. 2026-11-14 [자동 배포 실행](https://github.com/sugkamoney/geupjido-backend/actions/runs/43571745670)의 빌드·개발 배포도 성공했고, 배포 서버의 지도 상태는 다음 한국 시간 18시에 해당하는 `nextScheduledAt`을 반환했다. 당시 `lastCompletedAt`은 아직 `null`이었다. 따라서 **배포와 예약 활성화는 확인했지만 첫 예약 발행의 성공은 아직 확인하지 못했다.** 첫 실행 시각이 지난 뒤 위 3단계를 수행한다. 앞선 점검 근거는 [지도·월드컵 백엔드 QA 기록](../../../history/2026-11-14-map-worldcup-backend-qa.md)에 있다.
