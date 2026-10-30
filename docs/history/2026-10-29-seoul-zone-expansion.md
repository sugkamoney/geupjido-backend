# 서울 전역 권역 확대와 단지 재배정 (#101)

## 결정

- 서비스의 서울 범위를 기존 일부 법정동에서 서울 25개 구의 법정동 467개로 확대한다.
- 초기 권역은 `data/location/mapping/seoul-zone-decisions.tsv`를 단일 작업표로 관리한다. 이 표의 150개 권역은 법정동 경계로 표현 가능한 148개와 단지·행정동 경계로 나누는 2개를 포함한다.
- 배치표에 별도로 나온 생활권은 급지 차이가 0.2 이하여도 분리한다. 배치표에 없는 동은 인접 경계와 기존 초기 급지를 참고해 배정했으며, 이 값은 투표 기반 급지 점수가 아닌 초기 편집값이다. 표의 `근거` 열에서 원래 매핑과 추정 배정을 구분한다.
- 한 법정동이 여러 권역에 걸치는 경우는 법정동 코드만으로 단지를 배정하지 않고 단지 좌표와 추가 경계를 대조한다.

## 특수 경계

- 동부이촌은 이촌1동, 서부이촌은 이촌2동이다. 법정동 이촌동 경계와 2026-10-01 행정동 경계를 교차시켜 두 권역 폴리곤을 만든다.
- 목동 단지 권역은 목동신시가지 1~14단지의 지번 PNU로 조회한 필지 14개를 결합한다. 1~7단지는 목동, 8~14단지는 신정동에 있다. 일반 목동·신정 권역에서는 이 필지들을 빼서 공간 중복을 막는다. 이름에 숫자와 `단지`가 들어간 다른 아파트는 이 권역에 포함하지 않는다.
- 기존 지오코딩 점이 주소상 필지 밖에 있던 신시가지 5·6·9·14단지의 점은 해당 필지 내부 대표점으로 보정했다. 필지 경계와 단지 전체 부지는 완전히 같지 않을 수 있으므로 이후 지적 자료가 갱신되면 다시 검토한다.

## 데이터 출처와 재현 순서

1. `scripts/location/download_boundaries.py --env-file /path/to/local/.env`로 VWorld 법정동 경계 `LT_C_ADEMD_INFO`를 수집한다. GeoJSON은 Git에서 제외한다.
2. `scripts/location/download_ichon_admin_boundaries.py`로 2026-10-01 행정동 경계 자료에서 이촌1·2동을 추출한다. 현재 작업 파일은 `data/location/boundary/seoul-admin-dong-boundaries-20261001.geojson`이며 Git에서 제외한다. [행정동 경계 공개 가공본](https://github.com/vuski/admdongkor/tree/master/ver20261001)의 버전을 고정해 사용했다. 원자료는 국가데이터처 SGIS이며, 출처표시 조건에 따라 **SGIS·vuski/admdongkor**를 함께 표기한다.
3. `scripts/location/download_mokdong_parcels.py --env-file /path/to/local/.env`로 VWorld `LP_PA_CBND_BUBUN`의 신시가지 1~14단지 PNU 필지를 수집한다. 출력 GeoJSON은 Git에서 제외한다.
   `scripts/location/download_seoul_exception_parcels.py --env-file /path/to/local/.env`는 주소 검색 결과가 복수인 메세나폴리스(서교동 490)와 경계에서 벗어난 효창한신(효창동 5-145)의 필지를 같은 출처에서 확인한다. 두 필지 또한 Git에서 제외한다.
4. `scripts/location/compile_seoul_mapping.py`로 작업표를 일반 권역 매핑으로 컴파일한다. 일반 지역 적재기를 실행한 뒤 `scripts/location/apply_seoul_zone_decisions.py --apply`로 특수 경계, 초기 급지, 단지·거래 권역을 한 트랜잭션에 반영한다. `--apply`가 없으면 롤백 점검만 한다.
5. 새 법정동의 단지 목록·기본정보를 `scripts/complex/complete_import.py`로 재개 수집하고, 새 단지에 `scripts/complex/import_locations.py`를 실행한다. 이후 특수 경계 적용기를 다시 실행해 새 단지와 연결 거래의 권역을 검증한다.

## 로컬 검증 현황

- 최초 적용 당시 기존 서울 단지 2,914개는 모두 하나의 최종 권역에 들어갔다. 동부이촌 15개, 서부이촌 5개, 목동신시가지 14개로 확인했다. 기존 단지의 권역 239건과 연결 거래의 권역 3,560건을 변경했고, 사용하지 않는 옛 서울 권역 14개를 제거했다.
- 서울 법정동 467곳의 단지 목록 조회가 모두 종료됐다. 공공데이터 API가 간헐적으로 `04`를 반환해 재시도했으며, 최종 조회 상태는 Git에서 제외한 `data/complex/import-runs/local.jsonl`에 남겼다.
- 서울 단지 3,410개와 권역 150개를 로컬 PostGIS에서 확인했다. 저장된 단지의 `location`·`zone_id` 누락, 좌표 SRID 4326 위반, 단지와 연결 거래의 권역 불일치는 모두 0건이다. 모든 단지 점이 정확히 하나의 최종 권역 폴리곤에 포함됐다.
- 메세나폴리스는 도로명 지오코딩 결과가 복수였고, 효창한신의 자동 좌표는 해당 권역 경계에서 약 2m 벗어났다. 단지 ID·이름·법정동·지번을 확인한 뒤 VWorld의 해당 PNU 필지 내부 대표점을 사용했다. 재적재 시에도 `apply_seoul_zone_decisions.py`가 원본 필지를 검증하고 같은 보정을 적용한다.
- 공공데이터 원본 기본정보에서 세대수가 누락된 단지 3개(`A40010008`, `A10019934`, `A10020277`)는 `SOURCE_GAP`으로 기록했고, 실제 `complex` 행은 만들지 않았다. 필수값을 임의로 채우지 않으며, 원본이 보완되면 재수집한다. 즉 3,410개는 **저장 가능한 원본 단지의 완전한 적재 수**이고 원본 결측 3개는 별도로 남는다.
