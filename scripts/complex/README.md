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

현재 매핑의 법정동을 긴 시간에 걸쳐 재개할 때는 다음 관리 도구를 사용한다. 기존 매니페스트를 읽고 성공·빈 목록을 건너뛰며, 목록 API가 연속 실패하면 기본 120초 쉬고 다시 시작한다. 진전 없는 라운드가 연속되면 대기는 240초, 480초처럼 늘어나고 최대 900초다. 완료된 법정동 증가와 부분 적재의 신규 단지 수를 모두 진전으로 계산한다. 기본 3회 연속 진전이 없거나 오류 코드 `22`로 인한 한도 소진이 감지되면 자동으로 멈춘다. 한 번의 명령에서 최대 100회 재개하므로 무한 호출하지 않는다. 기본 배치 크기는 2개 법정동이다. 실행 중인 관리 도구를 중복 실행하지 않는다.

관리 도구는 매니페스트 옆의 `.lock` 파일에 프로세스 잠금을 유지한다. 같은 매니페스트로 두 번째 관리 도구를 실행하면 적재 전에 종료된다. 이 잠금은 관리 도구끼리만 조율하므로 `import_complexes.py --run`을 같은 매니페스트로 동시에 실행해서는 안 된다.

```sh
python3 -B scripts/complex/complete_import.py --manifest data/complex/import-runs/local.jsonl
```

#101의 서울 확대 범위만 재개하려면 현재 매핑에서 서울 법정동 467개가 먼저 배열되어 있으므로 다음과 같이 범위를 지정한다. 별도 작업 트리에서 실행할 때는 비밀값을 복사하지 않고 `--env-file`로 기존 로컬 파일을 지정한다. `SPRING_FLYWAY_ENABLED=false`는 로컬 DB에 현재 작업 트리보다 최신의 미병합 마이그레이션이 적용되어 있을 때에만 사용한다.

```sh
python3 -B scripts/complex/complete_import.py \
  --manifest data/complex/import-runs/local.jsonl \
  --start-index 0 --end-index 467 \
  --env-file /path/to/local/.env
python3 -B scripts/complex/import_locations.py --run --start-over \
  --env-file /path/to/local/.env
```

좌표 적재의 `--start-over`는 새로 수집한 단지 ID가 이전 커서보다 앞에 있을 수 있어 전체 미처리 단지를 다시 찾기 위한 것이다. 좌표나 권역이 비어 있는 단지는 재검사한다.

범위를 지정하지 않은 `complete_import.py`는 전체 매핑을 대상으로 하므로 과거에 적재됐지만 매니페스트에서 아직 완료 확인을 받지 않은 법정동도 다시 조회한다. 같은 단지는 DB에서 건너뛴다. 운영 DB나 월간 스케줄은 이 명령의 대상이 아니다.

#101의 초기 경기 승인 범위는 매핑 순서 467~615의 법정동 149개다. 2026-10-30 미배정 지역 확대 후 616~638에 새 법정동 23개를 추가했다. 서울 적재에 쓰던 매니페스트에는 과거 매핑 코드가 남아 있을 수 있으므로 경기용 매니페스트를 따로 쓴다. 먼저 `scripts/location/compile_gyeonggi_mapping.py`, 일반 지역 적재, `scripts/location/download_gyeonggi_exception_parcels.py`, `scripts/location/download_dasan_district_boundaries.py`, `scripts/location/apply_gyeonggi_zone_decisions.py --apply`를 실행해 새 권역을 준비한다. 다산진건·다산지금은 VWorld 공식 지구단위계획 경계를 사용한다. 일반 매핑 JSON의 경기 권역 128개 외에 다산지금·감이·관양·다산기타 4개 부분 권역을 적용기에서 추가한다. 다음 명령은 경기 단지를 수집하며, 완료 후 좌표 배치와 권역 적용기를 다시 실행한다. 일반 적재기를 다시 실행하면 부분 권역의 도형을 재적용해야 한다.

```sh
python3 -B scripts/complex/complete_import.py \
  --manifest data/complex/import-runs/gyeonggi-101.jsonl \
  --start-index 467 --end-index 616 \
  --env-file /path/to/local/.env
python3 -B scripts/complex/import_locations.py --run --start-over \
  --env-file /path/to/local/.env
python3 -B scripts/location/download_gyeonggi_exception_parcels.py \
  --env-file /path/to/local/.env
python3 -B scripts/location/download_dasan_district_boundaries.py \
  --env-file /path/to/local/.env
python3 -B scripts/location/apply_gyeonggi_zone_decisions.py --apply
```

확대된 23개 법정동을 별도로 확인할 때는 같은 경기 매니페스트로 `--start-index 616 --end-index 639`를 지정한다. 평택 세교동은 K-apt의 동삭동 표기와 현행 VWorld 주소·역조회 코드가 달라, 적용기가 확인한 두 단지의 법정동 코드만 조건부로 보정한다. 원본 지번주소 문자열은 출처 기록으로 유지한다.

단지가 많은 지역에서 기본 배치 크기 2개가 600초 제한에 걸리면 `--batch-size 1`로 재시작한다. 기존 매니페스트를 그대로 사용한다. 이전 배치가 결과를 기록하기 전에 종료됐더라도 이미 저장된 단지는 DB에서 건너뛴다. 동일 매니페스트를 쓰는 관리 도구는 동시에 두 개 실행하지 않는다.

앞쪽 법정동의 API가 연속 실패해 뒤쪽 미처리 지역까지 도달하지 못할 때는 `complete_import.py`에 `--max-consecutive-list-failures 20 --max-consecutive-api-failures 20`을 지정해 남은 지역을 한 번씩 확인한다. 두 옵션의 기본값 3은 유지하며, 전체 재개는 진전 없는 연속 실행 횟수 제한을 그대로 따른다.

목록 API에 있는 단지가 기본정보 API에서 성공 코드 `00`과 함께 필수값 없는 항목으로 반환될 수 있다. `data/complex/known-source-gaps.json`에 등록된 단지와 실패 사유만 남았을 때 해당 법정동을 `SOURCE_GAP`으로 기록한다. 다른 API·매핑 실패가 하나라도 함께 있으면 `PARTIAL`로 남아 재시도한다. 원본 결측 단지의 행은 생성하지 않으며, 전체 처리 수치에서는 실제 성공·빈 목록과 원본 결측을 구분해 출력한다. 이후 원본 값이 보완됐는지 확인하려면 `import_complexes.py --recheck-source-gaps`를 사용한다.

목록·기본정보 HTTP 요청에는 연결 10초, 응답 30초 제한이 적용된다. 배치 전체의 기본 제한 600초와는 별개다. 기본정보 조회의 일시적인 연결·응답 오류는 제한된 백오프로 재시도하고, 끝내 실패하면 다음 실행의 재시도 대상으로 남긴다.

HTTP `429`로 반환된 공공데이터포털 오류 본문에 `22`(호출 한도 초과)가 있으면 Java 배치가 해당 코드를 보존하고 관리 도구가 즉시 중단한다. 기본정보 API의 `22`가 확인된 동안에는 자동 재개를 반복하지 않는다. 호출 한도가 회복된 뒤 같은 매니페스트로 다시 실행하면 완료된 법정동은 건너뛰고 실패·미시도 법정동부터 처리한다.

## 단지 좌표·권역 수동 적재

`import_locations.py`는 기존 `ComplexLocationBatchRunner`를 최대 20건씩 순차 실행한다. 네이버 Maps Geocoding 인증 정보는 `NAVER_MAPS_CLIENT_ID`와 `NAVER_MAPS_CLIENT_SECRET` 환경 변수 또는 Git에서 제외된 로컬 `.env`에서 읽는다. 로컬 PostgreSQL·PostGIS와 API 이용 한도를 먼저 확인한다.

```sh
python3 -B scripts/complex/import_locations.py --run
python3 -B scripts/complex/summarize_location_runs.py --show-unresolved
```

실행 결과는 기본적으로 Git에서 제외된 `data/complex/location-runs/local.jsonl`에 단지별로 추가된다. 도구가 멈춘 뒤 같은 명령을 다시 실행하면 마지막으로 기록된 `nextAfterId`에서 재개한다. 처음부터 전체 ID를 다시 살펴볼 때만 `--start-over`를 지정한다. 좌표와 권역이 모두 저장된 단지는 DB 조회에서 건너뛴다. 주소 조회 결과가 없거나 권역이 미배정된 단지는 다음 전체 재검사에서 다시 후보가 될 수 있으므로, 무작정 반복 실행하지 말고 `--show-unresolved` 결과를 검토한다.

`--batch-size`는 1~20, `--max-batches`는 한 번의 명령에서 실행할 최대 구간 수이며 기본값은 각각 20과 250이다. 프로세스 제한 시간은 구간당 기본 180초다. 같은 매니페스트로 관리 도구를 동시에 실행하지 않도록 잠금 파일을 사용한다. 출력과 매니페스트에는 인증 정보나 API 응답 원문을 기록하지 않는다. `summarize_location_runs.py`는 단지별 마지막 시도만 집계하므로 최종 DB의 좌표·권역 개수도 별도로 확인한다.

### 사용자 검토 주소·권역 보정

`apply_reviewed_locations.sql`은 2026-10-27에 사용자가 확인한 단지 9건만 처리하는 수동 데이터 보정이다. 마장한영스테이의 지번·법정동 코드, 마포영화블렌하임의 도로명·좌표·권역, 방화동 주소 7건의 마곡 권역을 반영한다. 기존 값과 공간 포함 조건을 검사하고 모두 맞을 때 한 트랜잭션으로 저장한다. 기존 Flyway 마이그레이션을 변경하지 않는다. 로컬 DB에서는 다음과 같이 실행했고, 같은 명령을 다시 실행해도 성공하는 것을 확인했다.

```sh
docker exec -i -u postgres geupjido-postgres psql -U postgres -d geupjido -v ON_ERROR_STOP=1 -f - < scripts/complex/apply_reviewed_locations.sql
```

마곡 7건은 사용자 승인 예외이므로 원본 법정동 코드는 방화동인 채로 `zone_id`만 마곡이다. 일반 법정동 코드 일치 검사에서는 이 7개 ID를 별도로 집계한다. #90 당시 마장동 권역이 없어 마장한영스테이의 `zone_id`를 비워 두었고, #91에서 새 권역 추가와 함께 배정했다. 단지별 근거와 #90 수동 보정 당시 DB 수치는 `docs/history/2026-10-27-complex-location-batch.md`, 마장동 후속 결과는 `docs/history/2026-10-27-majang-zone.md`에 기록했다.

### #91 위치 예외 재검토

`audit_location_exceptions.sql`은 #90에서 보류한 12건, 마곡 수동 예외 7건, 부산 E2E 테스트 행의 존재 여부를 읽기 전용으로 출력한다. #91에서는 지번 후보가 VWorld PNU 필지 안에 있는 11건의 좌표·권역을 보정했다. `A10024245`는 K-apt의 사당동 42 필지가 현재 조회되지 않아, 서울시 단지 주소와 사당동 1161 필지를 따로 검증해 지번·좌표·권역을 보정했다. 필지별 근거와 원천 주소의 남은 이력 확인 사항은 `docs/history/2026-10-27-complex-location-exception-followup.md`에 기록했다.

```sh
docker exec -i -u postgres geupjido-postgres psql -U postgres -d geupjido -v ON_ERROR_STOP=1 -f - < scripts/complex/audit_location_exceptions.sql
```

11건의 조건부 보정 SQL은 저장된 원본 주소·좌표·법정동 코드와 대상 권역의 공간 포함 관계를 검사한다. 재실행하면 이미 배정된 상태를 확인하고 변경하지 않는다. **로컬 DB에서만** 다음과 같이 실행한다.

```sh
docker exec -i -u postgres geupjido-postgres psql -U postgres -d geupjido -v ON_ERROR_STOP=1 -f - < scripts/complex/apply_parcel_verified_locations.sql
```

사당동 단지 1건과 도로명주소 자치구 오기 2건은 각각 별도 조건부 SQL로 보정했다. 두 SQL 모두 원본 상태와 배정 권역의 공간 포함을 확인하며 재실행 시 변경 없이 종료한다. **로컬 DB에서만** 실행한다.

```sh
docker exec -i -u postgres geupjido-postgres psql -U postgres -d geupjido -v ON_ERROR_STOP=1 -f - < scripts/complex/correct_sadang_complex_location.sql
docker exec -i -u postgres geupjido-postgres psql -U postgres -d geupjido -v ON_ERROR_STOP=1 -f - < scripts/complex/correct_reviewed_road_districts.sql
```

`A10027875`는 수도권 수집 결과가 아니라 실제 API 저장 E2E 테스트가 로컬 DB에 남긴 부산 단지다. E2E 테스트는 이제 실행 후 롤백된다. **기존 로컬 DB에 남은 테스트 행만** 아래 SQL로 제거한다. 이 SQL은 단지 ID·주소·법정동 코드와 연결 거래가 예상과 다른 경우 중단한다. 운영 DB에서는 실행하지 않는다.

```sh
docker exec -i -u postgres geupjido-postgres psql -U postgres -d geupjido -v ON_ERROR_STOP=1 -f - < scripts/complex/remove_local_e2e_fixture.sql
```
