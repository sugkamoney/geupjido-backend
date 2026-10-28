# 실거래가 원본·단지 연결 검토 파일

> **적용 기준:** 현재 `location-mapping.json`의 권역 정의를 유지한다.
> PDF의 지명 차이를 이유로 권역을 변경하지 않는다. 사용자가 확인한
> 12조합만 `APPROVED`다. `리첸시아용산A`와 `한성1`은 `PENDING`이며
> 연결하지 않는다. 원본 거래는 그대로 보관한다.

`yongsan-202609-proposals.tsv`는 2026년 9월 용산구 로컬 거래에서 발견한
**14개 원본 단지명·법정동·지번·건축연도 조합**과 현재 단지 마스터 후보를 담는다.
14개 조합에 해당하는 거래는 18건이다. 이 중 12조합(거래 16건)은
사용자 승인에 따라 `APPROVED`, 2조합(거래 2건)은 `PENDING`이다.
각 조합의 공식 주소·사용승인일 재확인 결과는
[`yongsan-202609-review.md`](yongsan-202609-review.md)에 있다.

각 행의 `판정`은 후보 탐색 결과다. `NAME_EXACT`는 공백을 제외한 이름이 같다는
뜻이고, `NAME_REVIEW`는 이름이 다르다는 뜻이다. 어느 쪽도 동일 단지 확정은
아니다. 현재 후보는 같은 10자리 법정동 코드, 정확한 지번, 같은 건축·사용승인연도,
단일 후보 및 권역 존재를 만족했다. 실제 단지 동일성을 검토한 뒤에만 `결정`을
`APPROVED`로 바꾼다. 다른 단지라고 확인되면 `REJECTED`로 바꾼다.

첫 열 `계약월`은 `yyyyMM` 형식이며 승인 범위의 일부다. 같은 원본명·주소라도
다른 계약월은 별도 행으로 검토·승인해야 한다. 한 파일에 여러 지역과 계약월을
담을 수 있지만, 각 행이 실제로 해당 거래를 가리키는지 확인해야 한다.

수동 적용은 일반 서버 실행에서 꺼져 있다. 승인 후 다음처럼 한 번 실행한다.

```sh
./gradlew bootRun --args='--spring.main.web-application-type=none --app.apartment-trade-match.enabled=true'
```

기본 검토 파일은 2026년 9월 용산구 표본이다. 다른 검토 파일을 적용할 때에는
`APARTMENT_TRADE_MATCH_PROPOSAL_PATH`를 지정한다. 참조 파일과 권역
매핑을 바꾸려면 `APARTMENT_TRADE_MATCH_REFERENCE_PATH`,
`APARTMENT_TRADE_MATCH_MAPPING_PATH`를 지정한다. 이 작업은 승인된 행만
거래에 적용하고, 한 번에 트랜잭션으로 처리한다.
동일한 연결을 다시 실행하면 `이미 연결`로 집계한다. 기존의 다른 연결은
덮어쓰지 않고 실패한다. 다른 승인 행에서도 오류가 발생하면 이번 실행의
연결 전체가 롤백된다.

파일 이름의 `202609`와 각 행의 `계약월`은 **2026년 9월 계약 거래**를 뜻한다.
같은 이름·지번·연도의 다른 달 거래는 이번 실행에서 연결하지 않는다.
이 수동 실행은 첫 검증 표본용이다. 매달 서비스 대상 전 지역의 거래를
수집·판별·연결하는 흐름은 아직 구현되지 않았다. 새 월과 지역에 같은
승인 파일을 그대로 적용하지 않는다.

기존 DB 거래 전체의 분류·후보·연결 상태는 읽기 전용 수동 점검으로 확인한다.

```sh
./gradlew bootRun --args='--spring.main.web-application-type=none --app.apartment-trade-match.audit-enabled=true'
```

결과는 기본적으로 `build/reports/apartment-trade-match-audit.tsv`에 저장된다.
`APARTMENT_TRADE_MATCH_AUDIT_PATH`로 경로를 바꿀 수 있다. 거래 한 건마다
서비스 범위, 법정동 코드, 권역, 연결 상태, 미연결 거래의 후보 판정과 후보
단지 ID를 기록한다. `build/` 결과는 Git에서 제외된다. `INCONSISTENT_LINK`가
있으면 기존 단지·권역 연결을 검토해야 한다. 점검은 거래 DB 값을 수정하지
않으며, 승인 파일 적용과 별도로 실행한다.

같은 지번 후보가 없는 거래 5건과 서비스 범위 밖 거래 11건은 이 파일에 승인
대상으로 넣지 않았다. 조사 근거와 처리 기준은
[`docs/history/2026-10-28-trade-complex-matching-scope.md`](../../../docs/history/2026-10-28-trade-complex-matching-scope.md)에 있다.
