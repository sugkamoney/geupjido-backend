# geupjido-backend

수도권 아파트 급지 서비스의 백엔드 API입니다.

## 기술 스택

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.5
- PostgreSQL 16 + PostGIS 3.4
- Redis 7(로컬), Valkey(공유 개발 서버의 Redis 호환 저장소)

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

## 테스트

```bash
./gradlew test
```

월드컵 HTTP 투표부터 예약 집계와 지도 급지·단지 순위 반영까지 로컬 격리 DB에서 확인하려면 [가상 방문자 검증 절차](scripts/test/worldcup-virtual-voters-smoke.md)를 참고하세요. 검증용 선택은 공유 개발 DB에 기록하지 않습니다.

공유 개발 서버의 구성·배포·복구는 [배포 문서](docs/architecture/dev-server-deployment.md)와 [자동 배포 문서](docs/architecture/dev-auto-deployment.md), API 접속 규약은 [개발 API 연동 문서](docs/architecture/dev-api-frontend-handoff.md)를 참고하세요.

## 브랜치 전략

- `main`: 검증된 변경을 병합하면 공유 개발 서버에 자동 배포
- `develop`: 기능 통합과 CI 검증을 위한 기본 브랜치
- `feature/*`: 기능 개발
- `fix/*`: 개발 중 버그 수정
- `hotfix/*`: 운영 긴급 수정

커밋과 PR 제목은 Conventional Commits 형식을 사용합니다.
