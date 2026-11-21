# 아파트 매매 실거래가 API 소급 수집

## 흐름

권역 매핑의 시군구 코드 → 계약월별 국토교통부 API 조회 → 서비스 법정동 필터 → `apartment_trade` 거래 저장 → 단지 연결 검증 → `complex` 대표 가격·면적·계약일·잠정 순위 재계산.

API는 시군구와 계약월을 받아 거래를 반환하지만, 이 프로젝트의 `complex.id`는 제공하지 않는다. 저장 범위는 현재 권역 매핑에 포함된 법정동으로 한정한다. 코드가 확인되지 않는 법정동은 자동 저장하지 않고 확인 대상으로 남긴다. 원본을 받았다는 사실과 단지 가격이 채워졌다는 사실을 구분한다.

이미 저장된 서비스 범위 밖 거래는 `python3 -B scripts/trade/prune_out_of_scope.py`로 사전 건수를 확인하고 `--apply`로 정리한다. 실행 시 DB 백업 후 관련 충돌 원본과 거래를 한 트랜잭션으로 삭제한다. 연결된 단지·권역이 있거나 다른 범위의 충돌 원본이 참조하면 삭제를 중단한다.

## 실행 설정

로컬 프로필에서 다음 환경 변수를 설정한다. 실제 API 키는 저장소에 기록하지 않는다. `.env` 파일은 Spring Boot가 자동으로 읽지 않으므로 IDE 실행 설정이나 셸 환경으로 전달해야 한다.

| 변수 | 용도 |
|---|---|
| `DATA_GO_KR_SERVICE_KEY` | 공공데이터포털 일반 인증키 |
| `DATA_GO_KR_APT_TRADE_BASE_URL` | `https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade` |
| `DATA_GO_KR_APT_TRADE_ENABLED` | `true` |
| `APARTMENT_TRADE_BACKFILL_ENABLED` | `true`일 때 API 소급 수집 시작 |
| `APARTMENT_TRADE_BACKFILL_FROM_MONTH` | 시작 계약월, `yyyyMM` |
| `APARTMENT_TRADE_BACKFILL_THROUGH_MONTH` | 마지막 계약월, `yyyyMM` |
| `APARTMENT_TRADE_BACKFILL_MAX_PAIRS` | 한 실행에서 시도할 시군구·월 조합 수. 기본 40 |
| `APARTMENT_TRADE_BACKFILL_MANIFEST_PATH` | 결과 TSV 경로. 기본 `data/trade/import-runs/local.tsv` |

위 변수를 전달한 상태에서 `./gradlew bootRun --args='--spring.main.web-application-type=none'`으로 실행한다. 일반 서버 실행에는 Runner가 참여하지 않는다. 같은 결과 파일로 다시 실행하면 `SUCCESS`와 `PARTIAL`인 조합을 건너뛰고 `FAILED`인 조합은 다시 시도한다. `PARTIAL`은 충돌 없는 거래를 저장하고 충돌 건을 `apartment_trade_conflict`에 보관했다는 뜻이다. 완료 결과에는 계획 조합, 이전 완료, 이번 시도, 실패, 원본·신규·건너뛰기·갱신·보류 건수가 나온다. `실패사유`에는 실패 코드 또는 `SOURCE_CONFLICT:건수`가 기록된다. 기존 7열 결과 파일은 재실행 시 8열로 바뀌며 과거 실패의 사유는 빈칸으로 유지된다. 실제 오류 문장은 API 키를 포함할 가능성이 있어 결과 파일에 저장하지 않는다.

결과 TSV는 `./gradlew clean` 후에도 남도록 `build/` 밖에 저장하며 Git에서는 제외한다. 이 파일을 삭제하면 다음 실행에서 이미 저장된 월도 다시 API로 조회한다. 저장 자체는 중복 방지 규칙을 따른다.
재시도 행이 많을 때는 `python3 -B scripts/trade/summarize_import_manifest.py data/trade/import-runs/local.tsv --output-dir data/trade/import-runs`로 조합별 최신 상태와 시군구별 실패 사유를 따로 확인할 수 있다.

과거 계약월을 소급할 때는 `python3 -B scripts/trade/run_backfill.py --from-month 202501 --through-month 202509 --max-pairs 558 --manifest data/trade/import-runs/history-2025.tsv`를 사용할 수 있다. 2024년 전체를 조회할 때는 시작·종료월을 `202401`·`202412`, 최대 조합 수를 `744`로 바꾸고 별도 결과 파일을 지정한다. 로컬 `.env`에서 인증키를 읽되 출력하지 않으며, 결과 파일을 재사용하면 완료된 조합을 건너뛴다. 수집 후 `python3 -B scripts/trade/audit_unpriced_complexes.py`로 가격 공백 단지의 현재 원본 거래 유무, 지번·건축연도·이름 불일치를 읽기 전용으로 분류한다. 감사 CSV는 Git 제외 경로 `data/trade/audit-runs/unpriced-complexes.csv`에 생성되며 단지의 두 주소와 이름·연도가 같은 거래의 다른 지번도 담는다. 같은 지번·연도 거래가 있어도 임대 전용 여부와 복수 단지·도로명 주소를 확인하기 전에는 자동 연결 대상으로 간주하지 않는다.

현재 거래 식별 키는 API의 별도 거래 ID가 아니라 시군구·법정동·단지명·계약일·면적·층·가격이다. 소급 수집에서 이 키의 정상·해제 상태 또는 해제일이 충돌하면 해당 키의 API 응답을 `apartment_trade_conflict`에 건별로 보존하고, 나머지 거래만 저장한다. 이미 저장된 거래가 충돌에 걸리면 `existing_trade_id`로 연결해 대표 가격 후보에서 제외한다. 충돌 건을 자동으로 정상 또는 해제로 확정하지 않는다. API·매핑 오류 같은 다른 실패는 해당 시군구·월을 롤백하고 `FAILED`로 남긴다. 수동 월별 적재의 기존 전체 롤백 정책은 유지한다.

충돌 건 검토에는 다음 쿼리를 사용한다. `review_status='OPEN'`인 응답과 `existing_trade_id`의 저장 행을 함께 비교한다. 확인된 건만 별도 결정에 따라 처리하며, 재수집이 필요하면 새로운 manifest 경로를 사용하거나 기존 `PARTIAL` 행을 명시적으로 관리한다.

2026-10-29 로컬 검토용 전체 목록은 Git 제외 경로 `data/trade/audit-runs/trade-conflict-review-20261029.tsv`에 있다. API 응답 자체의 시군구·법정동 불일치 1건은 전체 점검 파일 `data/trade/audit-runs/after-partial-20261029.tsv`에서 `UNKNOWN_LEGAL_DONG`으로 찾을 수 있다.

```sql
SELECT c.district_code, c.deal_year_month, c.conflict_reason,
       c.raw_apartment_name, c.legal_dong_name, c.lot_number,
       c.apartment_dong, c.build_year, c.deal_date,
       c.exclusive_area, c.floor, c.deal_amount,
       c.is_canceled, c.cancellation_date, c.existing_trade_id,
       t.is_canceled AS stored_canceled,
       t.cancellation_date AS stored_cancellation_date
FROM apartment_trade_conflict c
LEFT JOIN apartment_trade t ON t.id = c.existing_trade_id
WHERE c.review_status = 'OPEN'
ORDER BY c.district_code, c.deal_year_month,
         c.raw_apartment_name, c.source_ordinal;
```

수집 후 단지 연결 상태는 [매칭 안내](matching/README.md)의 점검 경로로 확인한다. 단지에 연결되지 않은 거래는 대표 시세 계산에서 제외된다. 대표 시세의 선택 기준은 [설계 결정](../../docs/history/2026-10-28-complex-representative-price.md)에 있다.

대표 시세는 단지별로 정상 중개거래의 `58㎡ 이상 61㎡ 미만`, 중개거래의 `44㎡ 이상 65㎡ 이하`, 직거래의 같은 두 범위를 순서대로 찾는다. 해당 면적 거래가 없으면 중개거래, 직거래 순서로 다른 면적의 실제 거래 중 59㎡에 가장 가까운 면적을 고른다. 각 범위 안에서는 최근 계약일을 우선하고, 같은 날 여러 건이면 상위 중앙 거래를 택한다. 가격·실제 면적·계약일은 같은 거래에서 가져오고, 잠정 권역 순위는 대표 가격을 실제 면적으로 나눈 ㎡당 가격으로 계산한다. 미해결 원본 충돌과 해제 거래는 제외한다. 가격 공백과 원인은 `scripts/trade/report_complex_price_gaps.sql`로 확인한다. `complex.name`과 거래 API의 단지명은 다를 수 있으며, 이름만 같거나 다르다는 이유로 거래를 연결하거나 단지명을 덮어쓰지 않는다.

API 수집을 마친 뒤 엄격 일치 거래의 연결과 가격 재계산은 다음 실행에서 수행한다.

```sh
./gradlew bootRun --args='--spring.main.web-application-type=none --app.apartment-trade-match.auto-exact-enabled=true'
```

연결 기준과 보류 대상은 [매칭 안내](matching/README.md)에 기록했다. 추가로 API 원본을 수집한 뒤에는 이 실행을 다시 해야 새 거래의 연결과 대표 가격이 갱신된다. 일반 API 서버 실행에서는 자동 연결이 꺼져 있다.

전체 거래 후보 점검 Runner의 기본 결과는 `build/reports/apartment-trade-match-audit.tsv`이다. 장기 검토할 결과는 `data/trade/audit-runs/`로 복사하면 `clean build` 후에도 남고 Git에는 포함되지 않는다.

## 상세 API의 도로명 주소로 지번 불일치 거래 연결

기본 실거래가 API에는 K-apt 단지 ID가 없다. 별도로 활용신청한 [아파트 매매 실거래가 상세 자료](https://www.data.go.kr/data/15126468/openapi.do)는 `aptSeq`, 도로명·건물번호, 법정동 코드, 지번 구성값을 추가로 반환한다. `aptSeq`는 `11110-2234` 같은 실거래가 측 식별자이고, `complex.id`의 `A11055002` 같은 K-apt `kaptCode`와 값이 다르다. 두 ID를 같은 값으로 취급하지 않는다.

`scripts/trade/link_detailed_road_address.py`는 현재 로컬 PostgreSQL Docker DB의 **초기 적재용 수동 도구**다. 기존 감사에서 이름은 같은데 지번이 달라 보류된 거래를 대상으로 상세 API의 도로명 주소를 확인한다. 상세 응답과 기존 거래가 계약일·금액·면적·층·건축연도·단지명·지번·해제 상태까지 유일하게 대응하고, 법정동 코드·도로명 주소·단지명·권역이 일치하며, 같은 도로명 주소를 쓰는 다른 K-apt 단지가 없을 때만 연결한다. 확인된 임대 전용 단지는 제외한다. 한 `aptSeq`가 서로 다른 K-apt 단지로 이어지거나 한 원본 조합에 서로 다른 `aptSeq`가 나타나면 연결하지 않는다.

입력 `build/reports/trade-address-review-groups.tsv`는 거래별 감사 결과와 단지 마스터 CSV를 `scripts/trade/summarize_match_audit.py`로 요약해 만든다. `build/` 아래 파일은 `clean build`로 삭제되므로 재실행 전에는 최신 감사 결과에서 다시 생성한다. `.env`의 `DATA_GO_KR_SERVICE_KEY`를 읽지만 값은 출력하지 않는다. 다음 명령의 첫 실행은 DB를 변경하지 않는다.

```sh
python3 -B scripts/trade/link_detailed_road_address.py
python3 -B scripts/trade/link_detailed_road_address.py --apply
COMPLEX_PRICE_RECALCULATION_ENABLED=true ./gradlew bootRun --args='--spring.main.web-application-type=none'
```

`--apply`는 검증을 모두 마친 뒤 한 트랜잭션에서 거래의 `complex_id`와 `zone_id`를 함께 갱신한다. 갱신 행 수가 계획과 다르면 롤백한다. 적용 후 대표 시세 Runner를 별도로 실행해야 `complex.price`, `representative_area`, `last_deal_date`, 잠정 `zone_rank`가 바뀐다. 일반 API 서버 실행에는 이 수동 도구가 참여하지 않는다. 이 도구는 도로명 주소나 단지명이 다른 사례, K-apt 단지가 없는 거래와 서비스 권역 밖 거래를 자동 해결하지 않는다.

도로명 주소로 검증된 거래가 늘어난 뒤에는 `python3 -B scripts/trade/link_detailed_apt_seq.py`로 상세 API의 `aptSeq`가 기존 검증 연결의 K-apt 단지 하나에만 대응하는지 권역 매핑의 시군구·계약월에서 감사한다. 기본 범위는 2025년 10월~2026년 10월이며, 과거 거래까지 대조하려면 `--from-month 202501 --through-month 202610`을 지정한다. 연결 가능 건수와 충돌을 확인한 후 `--apply`로 갱신할 수 있다. 성공한 상세 응답은 Git 제외 경로 `data/trade/import-runs/detail-apt-seq-cache/`에 저장해 API 오류 후 재실행할 때 다시 쓰며, 최신 API 응답으로 다시 검사하려면 해당 캐시를 별도로 비우고 실행한다. 이때도 거래 한 행이 상세 응답 한 행과 유일하게 대응해야 하고, 법정동 코드·권역·임대 전용 여부를 다시 검사한다. 검증된 `aptSeq` 대응이 없는 거래는 이름이나 주소가 비슷해도 연결하지 않는다.

`scripts/trade/link_detailed_unique_road.py`는 지번이 다른 나머지 거래도
상세 API의 도로명 주소로 대조하는 로컬 초기 적재 도구다. 한 상세 응답이 저장
거래 한 행과만 대응하고, 거래 법정동명·상세 법정동 코드·단지 도로명 주소·
단지명·현재 권역이 모두 맞을 때만 연결한다. 동일 법정동에서 도로명 주소를
공유하는 K-apt 단지가 있거나 임대 전용이거나 `aptSeq`의 기존 연결·새 후보가
충돌하면 보류한다. 기본 실행은 읽기 전용이고 `--apply`를 지정한 때에만
`complex_id`와 `zone_id`를 한 트랜잭션으로 함께 갱신한다. 상세 API의
`aptSeq`를 K-apt 코드로 직접 변환하는 기능은 아니다.
조회하는 시군구·월은 로컬 DB에 저장된 거래가 있는 조합으로 한정한다.

```sh
python3 -B scripts/trade/link_detailed_unique_road.py
python3 -B scripts/trade/link_detailed_unique_road.py --apply
python3 -B scripts/trade/link_detailed_unique_road.py --from-month 202501 --through-month 202509 --apply
COMPLEX_PRICE_RECALCULATION_ENABLED=true ./gradlew bootRun --args='--spring.main.web-application-type=none'
```

과거 월 범위를 지정하면 그 기간에 저장된 거래만 대조한다. 상세 응답은
`link_detailed_apt_seq.py`와 같은 Git 제외 캐시에 저장되어 API 오류 후
재실행에 사용할 수 있다.

이 도구도 현재 로컬 Docker 컨테이너 `geupjido-postgres`와 `.env`의 인증키를
사용한다. 주간 갱신 흐름은 수집 후 엄격 일치 연결과 대표 가격 재계산을
이어 실행한다. 상세 API의 도로명 주소를 활용하는 이 초기 적재 도구는
주간 갱신에 포함되지 않는다.

## 주간 갱신 시험 실행

주간 갱신은 기본적으로 꺼져 있다. 전체 실행 전에 시군구 한 곳과 계약월
한 달로 수집 → 엄격 일치 연결 → 대표 가격 재계산을 시험할 수 있다.
API 수집만 한 조합으로 제한한다. 단지 연결과 대표 가격 재계산은 DB의
전체 대상에 실행된다. 시군구 코드는 현재 권역 매핑에 포함되어야 한다.
시험 실행도 로컬 DB를 변경하므로 기존 데이터와 실행 전후 건수를 확인한다.

| 변수 | 시험 실행 값 |
|---|---|
| `WEEKLY_TRADE_REFRESH_SMOKE_RUN_ON_STARTUP` | `true` |
| `WEEKLY_TRADE_REFRESH_SMOKE_DISTRICT_CODE` | 권역 매핑에 포함된 숫자 5자리 시군구 코드 |
| `WEEKLY_TRADE_REFRESH_SMOKE_DEAL_YEAR_MONTH` | `yyyyMM` 계약월 |
| `WEEKLY_TRADE_REFRESH_RUN_ON_STARTUP` | `false` |
| `WEEKLY_TRADE_REFRESH_SCHEDULE_ENABLED` | `false` |

`DATA_GO_KR_APT_TRADE_ENABLED=true`, API 주소·인증키와 로컬 PostgreSQL도
필요하다. 결과 파일은 기본적으로 `data/trade/import-runs/weekly-local.tsv`에
시군구·계약월별 수집 상태를 한 줄씩 추가한다. 실제 거래는 DB에 저장한다.
시험 실행에서 `COMPLETE`는 지정한 한 조합의 흐름이 끝났다는 뜻이며,
전체 주간 수집의 완료를 뜻하지 않는다.
단지 연결 단계는 과거 미연결 거래도 다시 확인한다. 따라서 로그의
`전체 법정동 미확인`과 `전체 권역 불일치`는 기존 DB 전체의 진단 수치다.
주간 실행의 `COMPLETE` 판정에는 이번에 수집한 시군구·계약월 조합의
미확인·불일치 건수만 사용한다. 수집 조합에 `FAILED`가 있으면 전체
상태는 `FAILED`, 이번 조합의 연결·권역 이상이 있으면 `NEEDS_REVIEW`다.
수집이 모두 `SUCCESS` 또는 `PARTIAL`이고 이번 조합의 연결·권역 이상이
없으면 완료로 판정한다. 이때 `PARTIAL`이 하나라도 있으면
`COMPLETE_WITH_CONFLICTS`, 모두 `SUCCESS`이면 `COMPLETE`다. 과거 이상이
이번 수집의 성공을 가로막지는 않지만 전체 진단 수치에서 계속 확인할 수 있다.

로컬 `.env`에 공공데이터포털 인증키가 있다면 아래 명령으로 한 조합만
실행할 수 있다. 실행 전 로컬 DB 백업을 만들고, 종료 후 결과 TSV와
DB 건수를 확인한다.
`PARTIAL`인 조합의 결과 TSV에는 `SOURCE_CONFLICT:건수`가 남으며,
충돌 원본은 `apartment_trade_conflict`에서 따로 확인한다.
결과 TSV의 `충돌`은 해당 실행에서 다시 본 원본 행을 포함한 건수이고,
`신규충돌`은 원본 내용이 충돌 확인 이력에 없어 처음 확인한 건수다.
V17의 `apartment_trade_conflict_seen`은 기존 충돌을 소급 등록하고,
응답 순서가 바뀌어도 원본 내용별 첫 발견 이력을 유지한다.
V18부터 검토 행은 `source_seen_id`로 해당 원본 내용에 연결한다.
`source_ordinal`은 마지막으로 관찰한 응답 순서일 뿐 검토 행의 식별자가
아니다. 같은 원본 내용에 중복으로 열려 있던 과거 검토 행은 삭제하지
않고 `SUPERSEDED`로 표시하며, 새 재수집은 원본 내용마다 `OPEN` 행을
최대 하나만 유지한다. 실제 정상·해제 충돌은 그대로 `OPEN`에 남는다.
기존 10열 결과 파일은 다음 실행 시 11열로 바꾸며, 과거 행의
`신규충돌`은 알 수 없으므로 빈칸으로 둔다.
서비스 대상 필터를 통과한 API 거래의 시군구 코드나 계약월이 요청
조합과 다르면 그 조합은 저장하지 않고 `MAPPING_FAILURE`로 기록한다.
주간 실행은 다음 조합의 수집을 계속하며, 전체 상태는 `FAILED`가 된다.

```sh
python3 -B scripts/trade/run_weekly_smoke.py --district-code 11170 --deal-year-month 202609
```

### 원본 로컬 DB를 보존하는 한 조합 검증

기존 명령은 로컬 `geupjido` DB를 직접 갱신한다. 원본을 보존하려면 로컬
PostGIS 컨테이너를 실행한 뒤 아래 격리 명령을 사용한다. `.env` 또는 현재
환경에 공공데이터포털 인증키가 필요하며, 키 값은 출력·커밋하지 않는다.

```sh
docker compose up -d postgres
python3 -B scripts/trade/run_weekly_isolated_smoke.py \
  --district-code 11140 --deal-year-month 202610
```

이 스크립트는 기존 로컬 DB를 `geupjido_weekly_trade_smoke`로 복제하고,
실행 환경의 DB 연결을 복제본으로 고정한다. 같은 이름의 DB가 이미 있으면
덮어쓰지 않고 중단한다. 대상 조합의 거래·연결 거래 수와 전체 단지의
가격 보유 수·가격 합계를 실행 전후에 출력한다. 원본 DB의 전후 집계가
같은지 확인하고, 정상 완료 또는 시험 실행 실패 후 복제 DB를 삭제한다.
중간에 프로세스를 강제 종료해 복제 DB가 남았다면 자동 재실행은 중단되며,
해당 DB를 확인한 뒤 별도로 정리해야 한다. API 수집은 한 조합이지만
정확 연결과 대표 가격 재계산은 복제 DB 전체에 적용된다.

2026-11-20에 중구 `11140`·`202610`을 복제 DB에서 실행한 결과, API
수집은 `SUCCESS`(신규 20건·기존 4건 갱신·충돌 0건), 전체 흐름은
`COMPLETE`였다. 대상 거래는 26→46건, 그중 연결 거래는 22→41건이었다.
가격 있는 단지는 5,609곳으로 유지됐고, 가격 합계는 569,117,448→
569,130,248만원으로 바뀌었다. 이번 조합의 법정동 미확인·권역 불일치는
모두 0건이다. 전체 법정동 미확인 8건은 이전 수집 데이터의 진단 수치다.
원본 로컬 DB의 거래·가격 집계는 변하지 않았으며 복제 DB도 삭제됐다.
이는 한 조합의 실 API·DB 검증 결과이며 전체 주간 대상이나 실제 예약
실행의 성공을 뜻하지 않는다.

### 개발 서버에서 한 조합 검증

같은 이슈에서 서버 검증까지 진행한다. 로컬 브랜치를 빌드하고, 생성된 JAR와
버전 관리 중인 입력 파일·설치 및 검증 스크립트를 EC2에 전송한다. 아래
`SSH_KEY_PATH`와 `DEV_HOST`는 작업자 환경에서 지정한다. 비밀번호와
공공데이터포털 인증키는 명령 인자·저장소 파일에 적지 않는다.

```sh
./gradlew clean build
scp -i "$SSH_KEY_PATH" \
  build/libs/geupjido-backend-0.0.1-SNAPSHOT.jar \
  scripts/deploy/install-dev-trade-inputs.sh \
  scripts/deploy/run-dev-weekly-trade-smoke.sh \
  data/location/mapping/location-mapping.json \
  data/location/reference/legal-dongs-20260917.tsv \
  ec2-user@"$DEV_HOST":/home/ec2-user/
```

EC2에서 `/etc/geupjido/dev.env`에 `DATA_GO_KR_SERVICE_KEY`를 안전하게
설정하고, 키 값 대신 설정 여부만 확인한다. 파일은 기존과 같이 root만
수정할 수 있게 유지한다. 예약 실행은 계속 비활성 상태로 둔다. 다음 명령은
추적 중인 입력 파일을 읽기 전용 경로에 설치하고, 결과 TSV를 쓸 경로를
`geupjido` 사용자에게 마련한다.

```sh
sudo bash /home/ec2-user/install-dev-trade-inputs.sh \
  /home/ec2-user/location-mapping.json \
  /home/ec2-user/legal-dongs-20260917.tsv
sudo bash /home/ec2-user/run-dev-weekly-trade-smoke.sh \
  /home/ec2-user/geupjido-backend-0.0.1-SNAPSHOT.jar 11140 202610
```

검증 스크립트는 입력 파일과 키 설정을 확인하고, RDS 데이터베이스를
`pg_dump -Fc`로 `/var/backups/geupjido/`에 백업한 뒤 실행한다. 1GiB EC2의
메모리를 위해 검증하는 동안 개발 API를 잠시 중지하고 종료 시 다시
시작해 health를 확인한다. 실행 전후 대상 거래·연결 거래 수와 전체 단지
가격 집계를 출력한다. 한 조합 결과는 `/var/lib/geupjido/trade/weekly-dev.tsv`
및 실행 로그에서 확인한다. 오류나 `NEEDS_REVIEW`는 성공으로 처리하지
않으며, 검증 실패 시에도 API 재시작을 시도한다. 백업 덤프의 보관·삭제는
검증 결과와 복구 필요 여부를 확인한 뒤 결정한다. JAR 롤백으로는 이미
변경된 DB 데이터가 복구되지 않는다.

2026-11-20 개발 RDS 첫 시험은 앱 시작 단계에서 중단됐다. 실거래가 범위
필터가 `app.apartment-trade-match.reference-path`를 별도로 사용하는데,
`dev` 프로필에 그 경로가 없었다. 수집 전 종료되어 거래·가격 집계가
26건·22건·5,609곳·569,117,448만원으로 유지됐고 개발 API도 다시
`UP`이었다. `dev` 프로필에 참조·매핑 경로를 추가한 JAR로 재실행한 결과,
중구 `11140`·`202610` 수집은 `SUCCESS`(원본 46건·신규 20건·갱신 4건·
충돌 0건), 전체 흐름은 `COMPLETE`(신규 연결 19건)였다. 대상 거래는
26→46건, 연결 거래는 22→41건, 가격 있는 단지는 5,609곳으로 유지됐고
가격 합계는 569,117,448→569,130,248만원이었다. 이번 조합의 법정동
미확인과 권역 불일치는 각각 0건이다. 결과 TSV를 확인했고, 개발 API
서비스와 외부 health는 다시 `UP`이었다. 검증 전 전체 DB 덤프는 EC2의
root 전용 경로에 보존했다. 개발 RDS의 한 조합 검증 결과이며 전체 주간
대상이나 예약 실행은 아직 검증하지 않았다.

2026-11-01 로컬 시험에서는 서비스 범위 거래 42건 중 신규 2건을 저장하고
모두 단지·권역에 연결했다. 문배동 `리첸시아용산B`의 동일 계약일·가격·면적·층
원본 2행은 정상 1행과 2026-10-08 해제 1행으로 함께 반환되어 `PARTIAL`로
기록됐다. 정상 행에는 103동과 2026-10-15 등기일이 있고, 해제 행에는
동·등기일이 없다. 현재 거래 식별 키에는 등기일이 없고 DB의 유일 제약도
이 두 행을 함께 저장할 수 없으므로 자동으로 정상 행을 덮어쓰거나 충돌을
해결 처리하지 않았다. 문배동의 `complex` 5개에도 해당 단지는 없어 이번
충돌이 서비스 단지의 대표 가격을 비우지는 않았다. 원본 두 행이 실제로
별도 계약인지 판단하려면 식별 규칙과 저장 구조를 함께 검토해야 한다.

V13은 API의 `rgstDate`를 `apartment_trade.registration_date`와
`apartment_trade_conflict.registration_date`에 보존한다. 재수집한 거래의
빠진 등기일은 보충하되, 기존 등기일과 다른 날짜가 오거나 기존 등기일보다
앞서 해제된 원본은 검토 행으로 분리한다. V14는 이 검토 사유를 DB 제약에
추가한다. V15·V16은 동일 계약 조건의 정상·해제 행을 각각 최대 한 건씩
저장하도록 유일 인덱스를 변경한다.

정상·해제 원본이 함께 반환될 때 정상 행에 동 정보와 등기일이 있고, 그
등기일이 해제 행의 해제일보다 늦으며 해제 행에는 등기일이 없는 경우만
서로 다른 거래로 취급한다. 기존 해제 행을 정상 행으로 덮어쓰지 않고
둘 다 보존한다. 같은 거래 조건이지만 이 근거가 부족하거나 등기일끼리
충돌하면 기존처럼 검토 대상으로 둔다. 등기일·동 정보가 실제 계약의
고유번호는 아니므로 이 규칙은 원본 자료에 근거한 보수적인 판정이다.

재실행 결과 용산구 2026-09 조합은 `SUCCESS`, 신규·갱신·충돌 0건,
전체 갱신 상태는 `COMPLETE`였다. 이번 조합의 법정동 미확인과 권역
불일치는 각각 0건이었다. 전체 미연결 거래에는 과거 시군구 코드와
법정동명이 맞지 않는 8건이 남아 있어 로그의 전체 미확인은 8건으로
표시된다. 이 원본은 자동으로 다른 구의 거래로 고치지 않는다.

## 전체 주간 갱신의 로컬 실행

### 원본 DB를 보존하는 전체 대상 검증

한 조합 검증을 마친 다음에는 전체 실행의 대상 수를 먼저 확인한다. 아래 첫
명령은 DB나 외부 API를 사용하지 않는다. 2026년 11월 현재 매핑에는
62개 시군구가 있어 최근 3개월 기준 186개 조합이다.

```sh
python3 -B scripts/trade/run_weekly_full_isolated.py --plan-only
docker compose up -d postgres
python3 -B scripts/trade/run_weekly_full_isolated.py --months-to-collect 3
```

전체 검증은 로컬 `geupjido`를 전용 `geupjido_weekly_trade_full_smoke`로
복제한다. 같은 이름의 DB가 있으면 기존 데이터를 덮어쓰지 않고 중단한다.
실행 환경에서 복제 DB 주소를 고정하고 예약 작업을 끈다. 조합별 결과는
Git에서 제외되는 `data/trade/import-runs/weekly-full-*.tsv`에 남긴다.
전체 실행 로그는 같은 위치의 `weekly-full-*-runN.log`에 권한 `0600`으로
보관하며 저장소에 올리지 않는다.
결과 파일의 조합 수·중복 여부와 실행 전후 거래·연결·충돌·대표 가격
집계를 확인한 후 복제 DB를 삭제하고 원본 DB 집계 유지 여부를 확인한다.

재실행 중복 검증이 필요하면 `--verify-rerun`을 추가한다. 이 옵션은 같은
복제 DB에서 전체 외부 API 호출을 한 번 더 수행하므로 호출량과 소요 시간이
두 배 가까이 늘어난다. `FAILED`나 연결 검토 상태가 있으면 결과 TSV와
실행 로그를 검토하고, 이 결과만으로 개발 서버의 주간 예약을 켜지 않는다.

로컬 DB를 백업한 뒤 아래 명령으로 현재 월을 포함한 최근 3개월의
서비스 대상 시군구를 한 번씩 수집한다. 이 스크립트는 예약 실행을 켜지
않으며, 조합별 결과는 Git 제외 경로
`data/trade/import-runs/weekly-local.tsv`에 기록한다.

```sh
python3 -B scripts/trade/run_weekly_refresh.py --months-to-collect 3
```

`prod` 프로필도 예약 기능은 기본 비활성이다. 서버에서 사용할 때는
`DATA_GO_KR_APT_TRADE_ENABLED=true`와 API 주소·인증키를 제공하고,
`WEEKLY_TRADE_REFRESH_SCHEDULE_ENABLED=true`를 명시적으로 설정한다.
권역 매핑·법정동 참조 파일 경로와 결과 파일 경로는 서버의 실제 접근
가능한 위치로 지정해야 한다. 예약 시각의 실행 확인은 배포 후 진행한다.

개발 서버의 `dev` 프로필에는 실거래 API·주간 갱신 설정을 기본 비활성으로
추가했다. 자동 배포 아티팩트에는 아직 입력 파일이 포함되지 않으므로,
`install-dev-trade-inputs.sh`로 버전 관리 중인 권역 매핑과 법정동 참조 파일을
서버에 설치해야 한다. 공공데이터포털 인증키는 서버의 비밀 환경 파일에만
둔다. 로컬의 한 조합 검증만으로 개발 서버 예약을 켜지 않는다. 서버의
API 인증·한 조합 결과·전체 실행 결과를 차례로 확인해야 한다.

전체 수동 실행용 [`run-dev-weekly-trade-full.sh`](../../scripts/deploy/run-dev-weekly-trade-full.sh)는
실행 전 RDS 덤프를 root 전용 디렉터리에 만들고 개발 API를 잠시 중지한다.
JVM 최대 힙을 448MiB로 제한한 별도 프로세스에서 최근 지정 개월의 전체
조합을 한 번 처리한 뒤 API를 재시작하고 내부 health를 확인한다. 전체 실행
중에는 개발 API를 사용할 수 없으므로 실행 시간과 사용자를 먼저 확인한다.
실행 전 EC2의 디스크 여유 공간과 메모리를 확인하고, 실행 중에는 별도
세션에서 메모리와 서비스 상태를 관측한다. 한 조합 검증에서 입력 파일과
인증키 설정을 마친 서버에 JAR와 이 스크립트를 전송한 뒤 실행한다.

```sh
df -h /var/backups/geupjido
free -m
sudo bash /home/ec2-user/run-dev-weekly-trade-full.sh \
  /home/ec2-user/geupjido-backend-0.0.1-SNAPSHOT.jar 3
```

스크립트는 전체 대상 수와 결과 TSV 행 수를 대조하고 거래·연결·충돌·가격
집계를 실행 전후에 출력한다. 상세 조합은 `/var/lib/geupjido/trade/`의
`weekly-full-*.tsv`, 전체 실행 로그와 백업은 `/var/backups/geupjido/`에
남긴다. 결과가 `FAILED` 또는 `NEEDS_REVIEW`이거나 API 복구가 실패하면
예약을 활성화하지 않는다. `COMPLETE_WITH_CONFLICTS`는 충돌 원본을
보류한 완료 상태이므로 그 자체가 예약 실행의 실패 조건은 아니다.
다만 수동 실행은 1GiB EC2에서 API를 중지했으므로, 예약 활성화 전에
API 동시 실행의 메모리·응답 영향을 검증하거나 별도 예약 배치의 API
중지·재시작 절차를 정한다.

전체 실행에서 `NEEDS_REVIEW` 또는 `FAILED`가 나오면 결과 TSV와 로그의
해당 조합을 확인하고 예약 실행을 중단한다. 공개된 가격을 이전 값으로
복원해야 한다면 DB 스냅샷 복원 계획이 필요하며 JAR 롤백만으로는
되돌아가지 않는다.

### 개발 서버의 주간 자동 실행

검증된 전체 실행 방식을 기존 EC2의 systemd 타이머로 예약한다. 별도
AWS 서비스는 사용하지 않는다. 한국 시간 월요일 03:00에 실행되며,
현재 매핑의 62개 시군구 × 최근 3개월을 매번 새로 수집한다. 수동 검증처럼
RDS 백업을 만든 다음 개발 API를 잠시 중지하고 448MiB 힙의 별도 JVM으로
처리한다. API는 성공·실패에 관계없이 재시작을 시도하고 내부 health를
확인한다. 기존 배포·롤백과 잠금을 공유하며, 배치 JVM은 최대 5분 뒤
종료된다. 셸의 복구 트랩이 완료되지 못해도 systemd 서비스의
`ExecStopPost`가 API 시작을 다시 시도한다. API 중지 시간은 평소 약
3분이었지만 외부 API와 서버 상태에
따라 더 길 수 있다. 타이머의 `Persistent=false`는 서버가 꺼져 있어
예약 시각을 놓쳤을 때 부팅 직후 예기치 않은 API 중지를 막는다.

[`install-dev-weekly-trade-timer.sh`](../../scripts/deploy/install-dev-weekly-trade-timer.sh)의
`install` 모드는 서비스·타이머 파일만 설치한다. 이 이슈의 저장 경계
수정이 `main`에 병합되고 자동 배포가 성공한 뒤 `enable` 모드로 예약을
활성화한다. API 프로세스의 Spring 예약 플래그와 외부 수집 플래그는
기본값 `false`로 유지한다.

로컬에서 네 파일을 EC2로 전송한다. `SSH_KEY_PATH`와 `EC2_TARGET`의
의미는 [개발 서버 배포 문서](../../docs/architecture/dev-server-deployment.md)의
수동 배포 절차와 같다.

```sh
ssh -i "$SSH_KEY_PATH" "$EC2_TARGET" 'mkdir -p ~/weekly-trade-timer'
scp -i "$SSH_KEY_PATH" \
  scripts/deploy/run-dev-weekly-trade-full.sh \
  scripts/deploy/install-dev-weekly-trade-timer.sh \
  scripts/deploy/geupjido-dev-weekly-trade.service \
  scripts/deploy/geupjido-dev-weekly-trade.timer \
  "$EC2_TARGET:~/weekly-trade-timer/"
```

EC2에서 먼저 설치하고, `main` 자동 배포가 끝난 뒤 활성화한다.

```sh
sudo bash /home/ec2-user/weekly-trade-timer/install-dev-weekly-trade-timer.sh install
systemctl list-timers --all geupjido-dev-weekly-trade.timer
sudo bash /home/ec2-user/weekly-trade-timer/install-dev-weekly-trade-timer.sh enable
systemctl list-timers --all geupjido-dev-weekly-trade.timer
```

다음 실행 뒤에는 `systemctl status geupjido-dev-weekly-trade.service`와
`journalctl -u geupjido-dev-weekly-trade.service -n 100 --no-pager`로
결과를 확인한다. 결과 TSV·백업·로그 위치는 전체 수동 실행과 같다.
백업은 자동 삭제하지 않으므로 디스크 사용량을 정기적으로 확인한다.
향후 예약을 끌 때는
`sudo systemctl disable --now geupjido-dev-weekly-trade.timer`를 실행한다.

2026-11-01 전체 시험에서는 62개 시군구의 2026-09~11월 총 186조합을
조회했다. `SUCCESS` 104조합, `PARTIAL` 82조합, `FAILED` 0조합이었다.
서비스 대상 원본 17,879건 중 신규 거래 1,023건, 기존 거래 갱신
1,666건, 원본 충돌 431건이 기록됐다. 새 거래 중 907건이 단지에
연결됐고, 단지 705곳의 대표값 또는 잠정 권역 순위가 바뀌었으며
그중 269곳의 대표 가격이 바뀌었다. 가격·면적·계약일·순위가 있는
단지는 실행 전후 모두 5,609곳이었다. 연결된 거래의 단지·권역
불일치와 단지 대표값의 일부 필드 누락은 0건이다.

당시 전체 종료 상태는 `NEEDS_REVIEW`였고 스크립트 종료 코드는 1이었다.
이는 원본 충돌도 실행 오류로 취급하던 이전 완료 판정 때문이다.
현재 판정에서는 API 실패와 이번 조합의 연결·권역 이상이 없으면
원본 충돌 82조합을 보존한 채 `COMPLETE_WITH_CONFLICTS`로 종료한다.
주로 동일 계약 조건의 정상·해제 응답인데, 남은 원본에는
서로 다른 계약임을 확인할 등기일·동 정보가 없는 사례가 있다.
같은 조합을 다시 수집해도 이 근거가 생기지 않으면 상태는
`PARTIAL`로 유지된다. 중구 2026-09 조합을 재실행해 신규·갱신 0건,
충돌 4건, DB 거래 36건과 열린 충돌 4건이 그대로인 것을 확인했다.
충돌 행을 자동 확정하거나 기존 거래를 덮어쓰지 않는다.

상태 분리 후 같은 중구 2026-09 조합을 다시 실행해 수집 `PARTIAL`,
최종 `COMPLETE_WITH_CONFLICTS`, 종료 코드 0을 확인했다. 신규·갱신 0건,
실행 전후 DB 거래 36건과 열린 충돌 4건은 같았다. 조합별 결과 파일에는
충돌 원본 4건이 계속 기록된다.
신규충돌 열을 추가한 뒤 다시 실행해도 원본 충돌은 4건, 신규충돌은
0건이었다. 기존 결과 파일의 193개 행은 그대로 보존하고 신규 열만
빈칸으로 추가된 것을 확인했다.

전체 186조합을 다시 실행한 결과도 `SUCCESS` 104·`PARTIAL` 82,
최종 `COMPLETE_WITH_CONFLICTS`, 종료 코드 0이었다. 원본 충돌 431건 중
신규충돌은 0건이고, 신규 거래·기존 거래 갱신도 0건이었다. DB 거래
433,635건, 가격 있는 단지 5,609곳은 실행 전후 같았다.
V17 소급 등록 후 중구 2026-09 조합을 다시 실행해 충돌 4건 중
신규충돌 0건과 정상 종료를 재확인했다.

## 검증된 원본 조합의 로컬 보정

2026-10-30에는 같은 지번에 K-apt 단지가 둘씩 있는 두 거래 조합을
법정동·지번·건축연도·단지명으로 재검증했다. 원본 거래 38건을
[`link_verified_name_lot_20261030.sql`](../../scripts/trade/link_verified_name_lot_20261030.sql)로
연결했다. 이 파일은 적용 전 후보·건수·미해결 충돌을 검사하고
`complex_id`와 `zone_id`를 함께 갱신한다. 이미 연결된 행은 바꾸지 않으며
원본 상태가 달라지면 트랜잭션을 중단한다. 실행 전 로컬 DB를 백업하고,
적용 후 `COMPLEX_PRICE_RECALCULATION_ENABLED=true`로 가격 Runner를
재실행한다. 적용 결과와 남은 공백 원인은
[대표 시세 결정 기록](../../docs/history/2026-10-28-complex-representative-price.md)에 있다.

## 가격 공백 단지 재대조

기본 감사는 현재 DB에 저장된 거래를 법정동·지번·이름으로
분류한다. 같은 지번을 공유하는 다른 K-apt 단지도 CSV에 표시한다.
이 결과만으로 가격을 입력하지 않는다.

```sh
python3 -B scripts/trade/audit_unpriced_complexes.py
python3 -B scripts/trade/audit_unique_parcel_name_variants.py
python3 -B scripts/trade/audit_unpriced_cached_road.py
```

두 번째 도구는 분양으로 확인된 단지 중 지번·연도·거래명이 각각
유일하고 이름 표기 일부가 일치하는 후보를 수동 검토용으로 낸다.
확인된 대응만 `ApprovedTradeNameAliases`에 법정동 코드·지번·연도·
거래명·K-apt 단지 ID 단위로 등록한다. 이후 기존 엄격 일치 배치를
실행하면 별칭 거래를 연결하고 대표 가격을 재계산한다.

마지막 도구는 이미 저장된 상세 API 캐시의 도로명주소와 현재 DB를
읽기 전용으로 대조한다. 분양형태·법정동·해제·미해결
충돌 및 다른 단지에 연결된 동일 원본 여부를 검사하며, 같은 도로명
주소를 공유하는 단지는 검토 상태로 남긴다. 건축연도와 사용승인연도를
비교하지 않고 단지명이 정확히 같고 주소·법정동·원본 거래가
유일하게 확인되면 연결할 수 있다. 확인된 단독 도로명 후보만 `--apply`로
연결한다. 적용 전 로컬 DB 백업을 만들고, 연결 후에는
대표 가격 재계산 Runner를 실행한다.

```sh
python3 -B scripts/trade/audit_unpriced_cached_road.py --apply
COMPLEX_PRICE_RECALCULATION_ENABLED=true ./gradlew bootRun --args='--spring.main.web-application-type=none'
```

2026-10-30 로컬 검증에서는 총 5,951단지 중 5,574단지에 대표
가격·면적·거래일·잠정 순위가 채워졌다. 남은 공백 377단지는
`data/trade/audit-runs/unpriced-complexes.csv`에서 원인별로 확인한다.

## 분양권·입주권 거래 원본

일반 아파트 매매와 별도인 [분양권 전매 API](https://www.data.go.kr/data/15126471/openapi.do)는
`apartment_presale_trade`에 저장한다. 단지명·법정동·지번이 모두
일치하는 단지 한 개씩 조회하며, 해제 행과 동일 거래의 중복 응답도
원본 그대로 보존한다. 기본 실행은 읽기 전용이고 `--apply`를 지정해야
로컬 PostgreSQL의 지정 단지·계약월 데이터를 트랜잭션으로 교체한다.
인증키는 환경 변수 또는 로컬 `.env`에서 읽고 출력하지 않는다.

```sh
python3 -B scripts/trade/import_presale_rights.py --complex-id A10020363 --from-month 202401 --through-month 202610
python3 -B scripts/trade/import_presale_rights.py --complex-id A10020363 --from-month 202401 --through-month 202610 --apply
```

2026-10-30 청담르엘 원본 23행을 저장했고, 그중 해제 표기는 2행이다.
일반 아파트 매매 후보가 없는 단지에는 해제·동일 거래 충돌을 제외한 권리
거래를 임시 대표 가격으로 사용할 수 있다. 수집만으로 가격은 바뀌지 않으므로
다음 재계산을 실행한다.

```sh
COMPLEX_PRICE_RECALCULATION_ENABLED=true ./gradlew bootRun --args='--spring.profiles.active=local --spring.main.web-application-type=none'
```

`complex.price_source`가 `PRESALE_OR_OCCUPANCY_RIGHT`인 가격은
“분양권·입주권 거래 기준 임시 가격”으로 표시한다. 일반 매매 거래가
연결되면 다음 재계산부터 이를 우선한다. 현재 단지 조회 API와 프론트엔드
화면은 이 저장소에 구현되어 있지 않다.
