# Orca 작업 인수인계

## 문서 목적

이 문서는 `geupjido-backend`의 현재 구현 상태와 작업 규칙을 Orca에 전달하기 위한 기준 문서다.

Orca에서 작업을 시작할 때 다음 순서로 확인한다.

1. 저장소 루트의 `AGENTS.md`
2. 이 문서
3. 현재 브랜치와 `git status`
4. 작업할 이슈와 관련 코드·테스트

이 문서는 2026-10-23에 작성하고 2026-10-25에 현재 이슈와 포일/내손 매핑 상태를 갱신했다. 이후 코드나 GitHub 이슈의 상태가 달라졌다면 실제 저장소와 GitHub 상태를 우선한다.

## 프로젝트 개요

- 저장소: `sugkamoney/geupjido-backend`
- 로컬 경로: `/Users/gimdonghyeon/Git/geupjido-backend`
- 목적: 지역·권역·공동주택·아파트 실거래가 데이터를 수집하고 서비스 API에 제공하는 백엔드 구축
- 주요 기술: Java 21, Spring Boot 3.5, Gradle, Spring Data JPA, PostgreSQL, PostGIS, Redis, Flyway
- 기본 브랜치 흐름: `feature/*`, `docs/*`, `test/*` → `develop` → `main`

현재는 Part 2 시스템 구축 단계 중 데이터 수집·저장 기반을 구성하고 있다. 아직 사용자용 Controller와 운영 배포 환경을 완성한 단계는 아니다.

## 현재 Git 작업 상태

- 진행 이슈: `#73 [DOCS/DATA] 작업 재개 점검 및 포일·내손 권역 매핑`
- 작업 브랜치: `docs/73-project-resumption-ai-collaboration`
- 직전 완료 작업: `#70 [DATA] 아파트 매매 실거래가 월별 수집 서비스 구성`
- 직전 반영 상태: 기능 브랜치에서 `develop`으로 병합했고, `develop`에서 `main`으로도 반영했다.

현재 브랜치에서 인수인계 문서와 `포일/내손` 매핑을 함께 변경했다. 권역은 162개, 매핑된 법정동 코드는 254개다. 사용자가 변경 내용을 검토한 뒤 직접 커밋, Push, Pull Request 생성과 병합을 진행한다.

## 협업 역할

### 사용자

- GitHub 이슈 생성
- 브랜치 생성과 체크아웃
- 핵심 비즈니스 로직 직접 작성, 서비스 정책과 설계의 최종 판단
- IntelliJ에서 커밋과 Push
- Pull Request 생성과 병합
- 최종 설계 판단과 범위 결정

### AI 작업자

- 다음 작업의 목적과 순서를 설명하고, 이슈마다 핵심 로직과 AI 담당 범위를 구분
- 생성하거나 수정할 파일의 정확한 경로 안내
- 한 번에 한 단계씩 코드와 이유 설명
- 합의된 이슈 범위의 설정 파일, 반복 코드, 테스트 코드, 문서 직접 수정
- AI가 작성한 코드의 변경 이유와 검증 결과를 사용자가 검토할 수 있게 설명
- 컴파일, 테스트, Git 상태와 변경 범위 검증
- 이슈, 브랜치, 커밋, Pull Request 제목과 본문 제안
- 완료 작업을 Notion의 시스템 구축·검증·개발 진행 일지에 반영

사용자의 명시적 요청 없이 브랜치 생성, 커밋, Push, Pull Request 생성 또는 병합을 대신하지 않는다.

## 작업 절차

1. 다음 작업 후보와 이유를 설명한다.
2. GitHub 이슈 제목, 내용, 완료 조건, 제외 범위, 라벨을 제안한다.
3. 사용자가 이슈를 만든 뒤 이슈 번호에 맞는 브랜치명을 제안한다.
4. 사용자가 최신 `develop`에서 브랜치를 만들고 체크아웃한다.
5. 관련 기존 코드를 먼저 확인한다.
6. 핵심 비즈니스 로직은 사용자에게 파일 경로, 작성 코드와 이유를 한 단계씩 안내한다. 합의한
   설정·반복 코드·테스트·문서는 AI가 작성한 뒤 변경 이유와 검증 결과를 함께 검토한다.
7. 좁은 범위 테스트부터 실행하고 마지막에 전체 빌드를 확인한다.
8. 기능 브랜치 → `develop` Pull Request를 만든다.
9. `develop` → `main` Pull Request를 만든다.
10. 완료 내용을 Notion에 기록한다.

범위를 키워야 할 때는 구현 전에 사용자와 협의한다. 새로운 요구사항을 현재 이슈에 임의로 추가하지 않는다.

## GitHub 규칙

### 브랜치명

형식:

```text
{type}/{issue-number}-{short-description}
```

예시:

```text
feature/70-apartment-trade-collection-service
test/49-complex-import-runner-e2e
docs/73-project-resumption-ai-collaboration
```

### 커밋 메시지

한 커밋에는 하나의 논리적 변경을 담는다.

```text
[#70] feat: 아파트 매매 실거래가 월별 수집 서비스 추가

- 월별 실거래가 API 조회와 도메인 변환 연결
- 정상·빈 응답·예외 흐름 테스트 추가
```

이슈가 없는 작업만 `[#이슈번호]`를 생략한다. 타입과 본문 세부 규칙은 `AGENTS.md`를 우선한다.

### Pull Request 제목

기능 브랜치에서 `develop`으로 병합할 때:

```text
feat: 아파트 매매 실거래가 월별 수집 서비스 구성
```

`develop`에서 `main`으로 병합할 때:

```text
[develop → main] feat: 아파트 매매 실거래가 월별 수집 서비스 반영
```

### Pull Request 본문

다음 형식을 유지한다.

```markdown
## 관련 이슈

- Closes #이슈번호

## 작업 목적

이번 작업이 필요한 이유를 작성한다.

## 주요 변경 사항

- 변경 사항
- 변경 사항

## 검증 결과

- 실행한 테스트와 결과
- 빌드 결과

## 참고 사항

- 제외 범위나 후속 작업
```

기능 브랜치 Pull Request에는 작업 성격에 따라 `data`, `database`, `test`, `documentation`, `ci` 라벨을 사용한다. `develop` → `main` Pull Request에는 `develop → main` 라벨을 사용한다.

## 코드 구조

주요 패키지의 역할은 다음과 같다.

```text
src/main/java/com/geupjido
├── batch
│   ├── location     # 행정경계와 지역·권역 초기 적재
│   ├── complex      # 공동주택 목록·기본정보 수집과 저장
│   └── trade        # 아파트 매매 실거래가 API 수집과 변환
├── complex
│   ├── domain       # 공동주택 단지 엔티티
│   └── repository   # 공동주택 단지 Repository
├── trade
│   ├── domain       # 아파트 매매 실거래가 엔티티
│   └── repository   # 아파트 매매 실거래가 Repository
└── zone             # 지역·도시·권역 및 인접 관계 엔티티와 Repository
```

외부 API 응답 DTO, 내부 수집 데이터, JPA 엔티티를 서로 구분한다. 외부 응답 필드명은 역직렬화 계약이므로 임의로 바꾸지 않으며, 내부에서 이해하기 쉬운 타입으로 변환한 뒤 사용한다.

## 구현된 데이터 흐름

### 지역·권역

```text
VWorld 행정경계 API
→ GeoJSON 다운로드
→ 법정동 코드와 Polygon·MultiPolygon 검증
→ 지역·도시·권역 매핑
→ PostgreSQL·PostGIS 적재
→ region, city, zone, zone_adjacency
```

`uiwang-naeson`은 `포일/내손` 한 권역이다. 내손동 `4143010700`과 포일동 `4143010900`을 함께 매핑하고 초기 급지 3.6을 사용한다. 경계 GeoJSON은 Git에서 제외되므로 새 환경에서는 `python3 -B scripts/location/download_boundaries.py`로 다시 생성한다. 2026-10-25 로컬 PostGIS 통합 테스트에서 두 법정동 경계가 같은 권역에 포함되는 것을 확인했다. 실제 단지의 권역 연결은 별도 기능이다.

### 공동주택 단지

```text
법정동 코드 목록
→ 공동주택 단지 목록 API
→ 단지별 기본정보 API
→ API DTO를 내부 데이터로 변환
→ Complex 엔티티 저장
→ 실패 단지 격리·집계
→ 제한 재시도
```

단지의 `legal_dong_code`는 공공데이터에서 받은 단지가 요청한 법정동에 속하는지 확인하고, 이후 지역·권역과 연결하기 위한 기준이다.

### 아파트 매매 실거래가

```text
시군구 코드 + 계약 연월
→ 아파트 매매 실거래가 API
→ ApartmentTradeApiItem
→ ApartmentTradeMapper
→ ApartmentTradeData
→ ApartmentTradeCollectionService
```

현재 여기까지 구현되어 있다. 수집된 `ApartmentTradeData`를 `ApartmentTrade` 엔티티로 변환하고 중복을 통제하면서 저장하는 서비스는 아직 구현하지 않았다.

## 완료된 주요 이슈

| 이슈 | 내용 |
| --- | --- |
| #1 | GitHub Actions 빌드 검증 구성 |
| #4 | Flyway 기반 지역·단지 초기 스키마 구성 |
| #7 | Codex 협업 및 프로젝트 작업 규칙 문서화 |
| #10 | 지역·권역 JPA 엔티티 및 Repository 구성 |
| #13 | GeoJSON 기반 지역·권역 초기 데이터 적재 |
| #16, #19 | 공동주택 목록·기본정보 API 클라이언트 구성 |
| #22, #25 | 공동주택 엔티티·Repository와 도메인 변환 구성 |
| #28, #31, #34, #37 | 공동주택 단건 수집·저장·E2E 검증·법정동 코드 저장 |
| #40, #43, #46, #49 | 공동주택 일괄 수집·저장·Runner·E2E 검증 |
| #52, #55 | 실패 단지 격리·집계와 제한 재시도 |
| #58, #61 | 아파트 매매 실거래가 API 클라이언트와 도메인 변환 |
| #64, #67 | 실거래가 스키마와 JPA 엔티티·Repository 구성 |
| #70 | 실거래가 월별 수집 서비스 구성 |

## 데이터베이스와 Flyway

마이그레이션 파일은 `src/main/resources/db/migration/`에 있다.

| 파일 | 역할 |
| --- | --- |
| `V1__enable_postgis.sql` | PostGIS 확장 활성화 |
| `V2__create_location_schema.sql` | 지역·도시·권역·인접 관계·단지 기본 스키마와 인덱스 생성 |
| `V3__expand_zone_tier_range.sql` | 권역 급지 허용 범위를 5.9까지 확장 |
| `V4__add_complex_legal_dong_code.sql` | 단지 법정동 코드 추가 |
| `V5__create_apartment_trade_table.sql` | 아파트 매매 실거래가 테이블과 인덱스 생성 |

규칙:

- 이미 적용된 Flyway 마이그레이션 파일을 수정하지 않는다.
- 스키마 변경은 다음 버전의 마이그레이션 파일로 추가한다.
- Hibernate가 운영 스키마를 자동 변경하게 두지 않는다.
- H2 테스트만으로 공간 타입과 PostgreSQL 제약조건을 검증했다고 판단하지 않는다.
- `apartment_trade`의 단지·권역 연결 컬럼은 현재 초기 수집을 위해 nullable이다. 연결 기준은 후속 작업에서 별도로 구현한다.
- 실거래가 중복 방지는 거래 식별 컬럼을 조합한 고유 인덱스와 애플리케이션 저장 정책을 함께 고려한다.

## 세부 구현 인벤토리

### 지역·권역 초기 적재

| 구성 요소 | 책임 |
| --- | --- |
| `LocationMappingReader` | JSON 매핑 파일을 읽고 코드 형식·중복 배정을 검증 |
| `LegalDongBoundaryReader` | GeoJSON을 읽고 법정동 코드와 도형 문자열로 변환 |
| `LegalDongBoundaryRepository` | 메모리에서 법정동 코드별 경계 도형을 조회 |
| `LocationDataImportRepository` | JDBC와 PostGIS 함수를 사용해 지역·도시·권역 데이터를 실제 DB에 저장 |
| `LocationDataImportService` | 매핑과 경계 데이터를 결합해 적재 흐름 실행 |
| `LocationDataImportRunner` | 설정이 활성화된 경우 애플리케이션 시작 시 초기 적재 실행 |

### 공동주택 단지 수집·저장

| 구성 요소 | 책임 |
| --- | --- |
| `ComplexListApiClient` | 법정동 코드로 공동주택 단지 목록 API 호출 |
| `ComplexBasicInfoApiClient` | 단지 코드로 공동주택 기본정보 API 호출 |
| `ComplexBasicInfoMapper` | 외부 기본정보 응답을 내부 저장 데이터로 변환·검증 |
| `ComplexListCollectionService` | 한 법정동의 전체 페이지를 순회해 단지 목록 수집 |
| `ComplexBasicInfoCollectionService` | 단지 한 건의 기본정보 수집과 변환 담당 |
| `ComplexBasicInfoImportService` | 신규 단지를 `Complex` 엔티티로 만들어 저장 |
| `ComplexBulkImportService` | 여러 법정동과 단지를 순회하며 일괄 저장, 실패 격리·집계·제한 재시도 |
| `ComplexBulkImportRunner` | 설정이 활성화된 경우 초기 일괄 적재 실행 |
| `ComplexRepository` | 단지 조회와 저장을 담당하는 Spring Data JPA Repository |

실패 결과는 `ComplexImportFailure`, `ComplexImportFailureType`, `ComplexImportStatus`, `ComplexBulkImportResult`로 표현한다. 외부 API 실패와 매핑 실패를 구분하며, 재시도 대상은 제한한다.

### 아파트 매매 실거래가 수집

| 구성 요소 | 책임 |
| --- | --- |
| `ApartmentTradeApiClient` | 시군구 코드와 계약 연월로 매매 실거래가 API 호출 |
| `ApartmentTradeApiItem` | 공공데이터 API 원본 응답 한 건 표현 |
| `ApartmentTradeMapper` | 문자열·숫자·날짜·취소 정보를 검증해 내부 데이터로 변환 |
| `ApartmentTradeData` | DB 저장 전 단계에서 사용하는 정규화된 내부 거래 데이터 |
| `ApartmentTradeCollectionService` | 월별 API 호출과 모든 응답 항목의 변환을 연결 |
| `ApartmentTrade` | `apartment_trade` 테이블에 대응하는 JPA 엔티티 |
| `ApartmentTradeRepository` | 실거래가 조회와 저장을 담당하는 Spring Data JPA Repository |

`ApartmentTradeCollectionService`와 `ApartmentTradeRepository` 사이를 연결하는 저장 서비스는 아직 없다.

## 주요 테스트 인벤토리

- API 계약: `ComplexListApiClientTest`, `ComplexBasicInfoApiClientTest`, `ApartmentTradeApiClientTest`
- 변환 규칙: `ComplexBasicInfoMapperTest`, `ApartmentTradeMapperTest`
- 단지 서비스: `ComplexListCollectionServiceTest`, `ComplexBasicInfoCollectionServiceTest`, `ComplexBasicInfoImportServiceTest`, `ComplexBulkImportServiceTest`
- Runner: `ComplexBulkImportRunnerTest`, `ComplexBulkImportRunnerE2ETest`
- 지역 데이터: `LocationMappingTest`, `LocationMappingReaderTest`, `LegalDongBoundaryReaderTest`
- 도메인: `ComplexTest`, `ApartmentTradeTest`
- PostgreSQL·PostGIS 통합: `LegalDongBoundaryRepositoryIT`, `LocationDataImportRepositoryIT`, `LocationDataImportServiceIT`, `ComplexRepositoryIT`, `ApartmentTradeRepositoryIT`
- 단지 저장 E2E: `ComplexBasicInfoImportServiceE2ETest`

테스트 이름만 보고 검증 범위를 추측하지 않는다. 작업 전 실제 테스트의 활성화 조건, Mock 범위와 DB 정리 방식을 읽는다.

## 다음 권장 작업

다음 후보는 다음 이슈다.

```text
[DATA] 아파트 매매 실거래가 월별 저장 서비스 구성
```

목표는 `ApartmentTradeCollectionService`가 반환한 `ApartmentTradeData` 목록을 `ApartmentTrade` 엔티티로 바꾸고 데이터베이스에 저장하는 것이다.

구현 전에 다음 결정을 사용자와 협의한다.

1. `ApartmentTradeData` → `ApartmentTrade` 변환을 별도 Mapper로 둘지 저장 서비스 내부 팩토리 메서드로 둘지
2. 저장 전에 Repository로 중복을 조회할지, 고유 제약조건 위반을 처리할지
3. 한 건 실패 시 월 전체를 롤백할지, 성공 건은 저장하고 실패 건만 격리할지
4. 단지·권역 연결은 이번 이슈에서 제외하고 원본 법정동·도로명 정보를 우선 저장할지

이 결정 없이 바로 구현하지 않는다.

## 로컬 실행 환경

초기 설정:

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

- PostgreSQL·PostGIS: 기본 로컬 포트 `5433`
- Redis: 기본 로컬 포트 `6379`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Actuator health: `http://localhost:8080/actuator/health`

`.env`에는 공공데이터 API 키와 VWorld API 키가 들어갈 수 있다. 실제 값, 토큰, 비밀번호를 코드·문서·로그·커밋에 포함하지 않는다.

주요 환경 변수 이름:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT

LOCATION_IMPORT_ENABLED
LOCATION_MAPPING_PATH
LOCATION_BOUNDARY_PATH
LOCATION_CODE_PROPERTY

COMPLEX_IMPORT_ENABLED
COMPLEX_IMPORT_MAPPING_PATH

DATA_GO_KR_SERVICE_KEY
DATA_GO_KR_APT_LIST_ENABLED
DATA_GO_KR_APT_LIST_BASE_URL
DATA_GO_KR_APT_BASIC_INFO_ENABLED
DATA_GO_KR_APT_BASIC_INFO_BASE_URL
DATA_GO_KR_APT_TRADE_ENABLED
DATA_GO_KR_APT_TRADE_BASE_URL
```

외부 API와 초기 적재 Runner는 기본적으로 비활성화한다. 테스트나 수집 목적을 명확히 한 경우에만 필요한 기능을 켠다.

## 검증 기준

기본 검증:

```bash
./gradlew test
./gradlew clean build
```

PostgreSQL·PostGIS 통합 테스트가 필요한 작업은 Docker를 실행한 상태에서 해당 환경 변수를 명시해 실행한다.

```bash
RUN_POSTGIS_IT=true ./gradlew test
RUN_COMPLEX_SAVE_E2E=true ./gradlew test
RUN_COMPLEX_BULK_IMPORT_E2E=true ./gradlew test
```

모든 변수를 매번 동시에 켜지 않는다. 변경 범위에 해당하는 테스트부터 실행하고, 마지막에 전체 빌드를 확인한다.

로컬에서 JDK 설치 경고가 출력되더라도 Gradle이 Java 21 toolchain을 사용해 빌드에 성공했는지 최종 결과로 판단한다. 데이터베이스 테스트 실패 시 먼저 Docker Desktop과 PostgreSQL 컨테이너 상태를 확인한다.

## Notion 기록

Notion의 `급지도 백엔드 설계 문서`를 프로젝트 진행 상태의 기준으로 사용한다. 코드 작업이 완료되면 GitHub 병합 여부와 실제 검증 결과를 확인한 뒤 관련 영역을 반드시 갱신한다.

프로젝트 기록 구조:

- [프로젝트 루트](https://app.notion.com/p/3cd9bfb122db80ada91cda2517198628)
- [개발 진행 일지](https://app.notion.com/p/99ef16b557c24d9dafeb019c5a64a1b3)
- [Part 1. 설계 기준](https://app.notion.com/p/ea9a72c5ce6e4a3cb8bc8b4b83252628)
- [Part 2. 시스템 구축](https://app.notion.com/p/bacaaadfc1184e56b25adffca31fddda)
- [Part 3. 검증과 튜닝](https://app.notion.com/p/ac0983df9d464b80b05302235e16788d)
- [Part 4. 학습 기록](https://app.notion.com/p/707f78af1136421899072f06f3c12987)
- [결정해야 할 것](https://app.notion.com/p/1c78660461004db282a36ac47173ebed)

각 영역에는 다음 내용을 기록한다.

- 개발 진행 일지: 작업 날짜, 이슈·브랜치·Pull Request, 완료 내용, 검증 결과, 현재 단계, 다음 작업
- Part 1. 설계 기준: 장기간 유지할 도메인·아키텍처·브랜치·운영 원칙이 새로 결정되거나 변경된 경우
- Part 2. 시스템 구축: 실제로 추가된 구조, 데이터 흐름, 구현 범위와 현재 구축 단계
- Part 3. 검증과 튜닝: 실행한 테스트, 결과, 발견한 문제, 해결 과정과 성능·안정성 개선 내용
- Part 4. 학습 기록: 작업 중 새로 이해한 Java·Spring·JPA·데이터베이스·공간 데이터·외부 API 개념과 프로젝트 적용 방식
- 결정해야 할 것: 구현 전에 사용자 판단이 필요한 미확정 항목, 선택지와 장단점

모든 작업이 모든 Part를 바꾸는 것은 아니다. 다만 작업이 끝날 때마다 여섯 영역을 확인하고, 해당되는 곳은 빠뜨리지 않고 갱신한다. 최소한 `개발 진행 일지`에는 완료한 작업과 다음 단계가 남아 있어야 한다.

문서에 아직 검증하지 않은 내용을 완료로 기록하지 않는다.

## 주의사항

- 사용자의 요청 없이 작업 범위를 확장하지 않는다.
- 코드만 던지지 말고 왜 필요한지, 데이터가 어떻게 흐르는지 설명한다.
- 한 번에 많은 파일을 작성하도록 요구하지 않고 단계별로 진행한다.
- 외부 API 원본 값, 내부 데이터, JPA 엔티티의 책임을 섞지 않는다.
- JPA 연관관계와 지연 로딩은 트랜잭션 범위를 고려한다.
- 데이터베이스 기능은 PostgreSQL·PostGIS에서 확인한다.
- 기존 Flyway 파일은 수정하지 않는다.
- 사용자가 IntelliJ에서 커밋한 뒤 추가 수정이 생기면 다시 스테이징해야 한다.
- `.env`, API 키, 실제 GeoJSON, `.DS_Store`를 커밋하지 않는다.

## 아직 구현하지 않은 범위

- 실거래가 월별 DB 저장 서비스
- 실거래가 저장 시 중복·부분 실패 정책
- 실거래가와 공동주택 단지 매칭
- 실거래가와 권역 연결 및 집계
- 사용자용 조회 Controller와 API 응답 DTO
- 사용자, 투표, 급지 산정, 리포트 도메인의 실제 구현
- 인증·인가와 글로벌 예외 응답 체계
- 운영 PostgreSQL·Redis 생성과 비밀값 관리
- EC2 또는 다른 실행 환경의 배포 자동화
- 운영 모니터링, 백업과 장애 복구 절차

기존 `user`, `vote`, `tier`, `report`, `match`, `transaction` 등의 빈 `package-info.java`는 향후 경계를 표시하는 자리다. 구현이 완료된 패키지로 오해하지 않는다.

## Orca 사전 점검 절차

Orca는 코드 작성 전에 이 문서 자체를 먼저 검토한다. 검토 결과가 사용자의 승인을 받기 전에는 다음 기능을 구현하지 않는다.

다음 항목을 실제 저장소와 대조한다.

1. 현재 브랜치, 최신 커밋과 GitHub 이슈·Pull Request 상태
2. 이 문서의 클래스·파일·마이그레이션 이름이 실제 경로와 일치하는지
3. 완료되었다고 적힌 흐름에 실제 구현과 테스트가 모두 존재하는지
4. `AGENTS.md`, README, CI, 애플리케이션 설정과 충돌하는 규칙이 있는지
5. 다음 작업에 필요한 결정이 빠져 있거나 이미 결정된 내용을 다시 묻고 있지 않은지
6. 비밀값, 개인 정보, 로컬 전용 경로 등 공유하면 안 되는 정보가 문서에 포함되었는지
7. 오래되었거나 단정할 근거가 부족한 설명이 있는지

결과는 다음 형식으로 사용자에게 먼저 보고한다.

```text
## 인수인계 문서 점검 결과

### 반드시 수정할 내용
- 사실과 다른 내용 또는 작업을 막는 누락

### 보완을 권장하는 내용
- 작업은 가능하지만 설명을 추가하면 좋은 부분

### 확인된 현재 위치
- 마지막 완료 작업
- 다음 후보 작업
- 구현 전 결정할 사항
```

## Orca 시작 프롬프트

Orca의 새 작업에 다음 내용을 전달한다.

```text
먼저 저장소 루트의 AGENTS.md와
docs/handoff/2026-10-23-orca-handoff.md를 끝까지 읽어줘.

현재 저장소와 GitHub 상태를 확인한 뒤 인수인계 문서부터 점검해줘.
클래스·마이그레이션·설정·테스트 경로를 실제 파일과 대조하고,
AGENTS.md와 충돌하거나 빠진 내용, 오래된 내용, 공유하면 안 되는 내용이 있는지 확인해줘.
결과를 '반드시 수정할 내용 / 보완을 권장하는 내용 / 확인된 현재 위치'로 나눠 먼저 보고해줘.
내가 점검 결과를 확인하기 전에는 코드를 작성하거나 파일을 수정하지 마.

나는 GitHub 이슈 생성, 브랜치 체크아웃, 비즈니스 코드 작성,
커밋·Push·PR·병합을 직접 한다.

너는 한 번에 한 단계씩 다음 내용을 안내해줘.
- 정확한 파일 경로
- 작성하거나 수정할 코드
- 해당 코드가 필요한 이유
- 데이터 흐름에서의 위치
- 실행할 테스트와 기대 결과

다음 후보 작업은 아파트 매매 실거래가 월별 저장 서비스다.
바로 구현하지 말고, 인수인계 문서의 '다음 권장 작업'에 적힌
범위와 중복 처리 전략부터 나와 협의해줘.
```

## 작업 시작 체크리스트

- [ ] `AGENTS.md`를 읽었다.
- [ ] 이 인수인계 문서를 읽었다.
- [ ] 현재 브랜치와 `git status`를 확인했다.
- [ ] 관련 이슈의 목적·완료 조건·제외 범위를 확인했다.
- [ ] 관련 코드와 테스트를 먼저 읽었다.
- [ ] 사용자와 이번 작업의 범위를 합의했다.
- [ ] 좁은 범위 테스트와 전체 빌드 계획을 세웠다.

