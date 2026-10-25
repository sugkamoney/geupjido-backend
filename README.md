# geupjido-backend

수도권 아파트 급지 서비스의 백엔드 API입니다.

## 기술 스택

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.5
- PostgreSQL 16 + PostGIS 3.4
- Redis 7

로컬 PostgreSQL은 다른 프로젝트와의 충돌을 피하기 위해 `5433` 포트를 사용합니다.

## 로컬 실행

Java 21과 Docker가 필요합니다.

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 아파트 매매 실거래가 수동 적재

로컬 PostgreSQL·PostGIS를 실행하고, 공공데이터포털 인증키와 실거래가 API 기본 URL을 각각 `DATA_GO_KR_SERVICE_KEY`, `DATA_GO_KR_APT_TRADE_BASE_URL` 환경 변수로 설정합니다. 인증키를 명령 인수나 저장소 파일에 넣지 않습니다.

한 시군구·한 계약월을 적재하려면 다음처럼 실행합니다. 아래 코드는 입력 형식의 예시이며, 실제 실행 전 원하는 시군구 코드와 계약월로 바꿉니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local --spring.main.web-application-type=none --app.external.apartment-trade.enabled=true --app.apartment-trade-import.enabled=true --app.apartment-trade-import.district-code=11170 --app.apartment-trade-import.deal-year-month=202609'
```

`app.apartment-trade-import.enabled`의 기본값은 `false`입니다. 따라서 일반 서버 실행에서는 적재가 시작되지 않습니다. 적재 결과는 전체·신규·건너뛰기·갱신 건수로 기록됩니다. 같은 월을 다시 실행하면 동일 거래는 건너뛰며, 해제 정보가 충돌하면 그 실행의 월별 저장을 롤백합니다. 현재 실거래가와 단지·권역은 연결하지 않습니다.

실제 외부 API 인증키 없이도 고정 응답과 로컬 PostgreSQL·PostGIS를 사용하는 통합 테스트를 실행할 수 있습니다.

```bash
RUN_POSTGIS_IT=true ./gradlew test --tests com.geupjido.batch.trade.runner.ApartmentTradeImportRunnerIT
```

## 테스트

```bash
./gradlew test
```

## 브랜치 전략

- `main`: 운영 배포
- `develop`: 개발 서버 배포 및 기본 브랜치
- `feature/*`: 기능 개발
- `fix/*`: 개발 중 버그 수정
- `hotfix/*`: 운영 긴급 수정

커밋과 PR 제목은 Conventional Commits 형식을 사용합니다.
