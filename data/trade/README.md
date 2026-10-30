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
사용한다. 정기 배치에 자동 포함되지는 않으므로 새 실거래가를 주간 수집한
뒤에는 별도 연결·가격 재계산 흐름이 필요하다.

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
