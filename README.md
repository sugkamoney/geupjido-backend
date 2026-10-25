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
