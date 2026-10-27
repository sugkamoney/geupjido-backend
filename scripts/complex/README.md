# 공동주택 단지 수동 적재 배치

`import_complexes.py`는 `data/location/mapping/location-mapping.json`의 `zones[].dongCodes`를 순서대로 읽고, 기존 `ComplexBulkImportRunner`를 작은 배치로 호출한다. 기본 배치 크기는 5개이며 1~20개 사이에서 지정할 수 있다. Python 표준 라이브러리만 사용한다.

먼저 로컬 PostgreSQL과 `.env`의 공동주택 목록·기본정보 API 설정을 준비한다. 실제 실행은 수동 적재만 켜고 월간 예약·다른 적재 작업과 웹 서버는 끈다. API 키와 URL은 환경 변수로만 전달하며 명령행 인수나 매니페스트에 기록하지 않는다. 스크립트는 Gradle 및 애플리케이션 원문 로그를 콘솔에 출력하지 않는다.

```sh
python3 scripts/complex/import_complexes.py --list
python3 scripts/complex/import_complexes.py --dry-run --batch-size 1 --start-index 15 --end-index 35 --manifest data/complex/import-runs/local.jsonl
python3 scripts/complex/import_complexes.py --run --batch-size 1 --start-index 15 --end-index 35 --manifest data/complex/import-runs/local.jsonl
```

매니페스트에는 법정동 코드별 최신 시도의 `status`, 신규·건너뛰기·실패 수, 분류된 실패 사유, 실패 단지 코드, 기록 시간이 JSONL로 누적된다. `SUCCESS`, `EMPTY`, `SOURCE_GAP`은 다음 실행에서 건너뛰고 `PARTIAL`, `LIST_FAILED`, `PROCESS_FAILED`는 재시도한다. `SOURCE_GAP`은 단지를 저장했다는 뜻이 아니라 확인된 원본 결측만 남은 법정동을 별도로 집계한다는 뜻이다. 미시도 법정동을 먼저 처리하고 기존 실패 코드는 그다음에 재시도한다. 프로세스가 비정상 종료하거나 예상한 결과 로그가 없으면 해당 배치의 미확인 코드를 `PROCESS_FAILED`로 기록하고 중단한다. 실패 상세 원문은 API 키가 포함될 수 있어 저장하지 않고 고정된 사유 분류만 남긴다. 원문 분석이 필요하면 안전한 로컬 실행 로그를 별도로 확인한다.

`--start-index`는 0부터 시작하는 매핑 순서이며 `--end-index`는 그 위치를 포함하지 않는다. 예시의 15~35는 16번째부터 35번째까지 20개 법정동이다. 같은 매니페스트 경로로 명령을 다시 실행하면 성공·빈 목록 코드는 건너뛰고 실패 코드를 재시도한다. 기본 실행 제한은 배치당 600초다. 목록 API가 3개 법정동 연속 실패하거나, 목록 실패와 신규·기존 단지 저장 없이 기본정보만 실패한 법정동이 합쳐서 3곳 연속되면 더 이상 호출하지 않는다. 제한은 각각 `--timeout-seconds`, `--max-consecutive-list-failures`, `--max-consecutive-api-failures`로 조절할 수 있다. 일부 단지가 저장되거나 기존 단지로 확인된 `PARTIAL`은 전체 API 실패로 세지 않는다. 매니페스트는 Git에서 제외되며 로컬에 보존된다. 사전 검증과 오프라인 테스트는 다음 명령으로 실행한다.

```sh
python3 -m unittest discover -s scripts/complex -p 'test_*.py' -v
```

전체 254개 법정동을 긴 시간에 걸쳐 재개할 때는 다음 관리 도구를 사용한다. 기존 매니페스트를 읽고 성공·빈 목록을 건너뛰며, 목록 API가 연속 실패하면 기본 120초 쉬고 다시 시작한다. 진전 없는 라운드가 연속되면 대기는 240초, 480초처럼 늘어나고 최대 900초다. 완료된 법정동 증가와 부분 적재의 신규 단지 수를 모두 진전으로 계산한다. 기본 3회 연속 진전이 없거나 오류 코드 `22`로 인한 한도 소진이 감지되면 자동으로 멈춘다. 한 번의 명령에서 최대 100회 재개하므로 무한 호출하지 않는다. 기본 배치 크기는 2개 법정동이다. 실행 중인 관리 도구를 중복 실행하지 않는다.

관리 도구는 매니페스트 옆의 `.lock` 파일에 프로세스 잠금을 유지한다. 같은 매니페스트로 두 번째 관리 도구를 실행하면 적재 전에 종료된다. 이 잠금은 관리 도구끼리만 조율하므로 `import_complexes.py --run`을 같은 매니페스트로 동시에 실행해서는 안 된다.

```sh
python3 -B scripts/complex/complete_import.py --manifest data/complex/import-runs/local.jsonl
```

`complete_import.py`는 전체 매핑을 대상으로 하므로 과거에 적재됐지만 매니페스트에서 아직 완료 확인을 받지 않은 법정동도 다시 조회한다. 같은 단지는 DB에서 건너뛴다. 운영 DB나 월간 스케줄은 이 명령의 대상이 아니다.

단지가 많은 지역에서 기본 배치 크기 2개가 600초 제한에 걸리면 `--batch-size 1`로 재시작한다. 기존 매니페스트를 그대로 사용한다. 이전 배치가 결과를 기록하기 전에 종료됐더라도 이미 저장된 단지는 DB에서 건너뛴다. 동일 매니페스트를 쓰는 관리 도구는 동시에 두 개 실행하지 않는다.

목록 API에 있는 단지가 기본정보 API에서 성공 코드 `00`과 함께 필수값 없는 항목으로 반환될 수 있다. `data/complex/known-source-gaps.json`에 등록된 단지와 실패 사유만 남았을 때 해당 법정동을 `SOURCE_GAP`으로 기록한다. 다른 API·매핑 실패가 하나라도 함께 있으면 `PARTIAL`로 남아 재시도한다. 원본 결측 단지의 행은 생성하지 않으며, 전체 처리 수치에서는 실제 성공·빈 목록과 원본 결측을 구분해 출력한다. 이후 원본 값이 보완됐는지 확인하려면 `import_complexes.py --recheck-source-gaps`를 사용한다.

목록·기본정보 HTTP 요청에는 연결 10초, 응답 30초 제한이 적용된다. 배치 전체의 기본 제한 600초와는 별개다. 기본정보 조회의 일시적인 연결·응답 오류는 제한된 백오프로 재시도하고, 끝내 실패하면 다음 실행의 재시도 대상으로 남긴다.

HTTP `429`로 반환된 공공데이터포털 오류 본문에 `22`(호출 한도 초과)가 있으면 Java 배치가 해당 코드를 보존하고 관리 도구가 즉시 중단한다. 기본정보 API의 `22`가 확인된 동안에는 자동 재개를 반복하지 않는다. 호출 한도가 회복된 뒤 같은 매니페스트로 다시 실행하면 완료된 법정동은 건너뛰고 실패·미시도 법정동부터 처리한다.
