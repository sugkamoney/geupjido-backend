# 공유 개발 서버 배포

## 구성

- 개발용 EC2: Amazon Linux 2023 Arm, Java 21, 로컬 루프백에 바인딩된 Valkey
- 개발용 RDS: PostgreSQL 16 + PostGIS, EC2와 같은 VPC의 비공개 접근
- 애플리케이션: `dev` 프로필의 JAR를 `geupjido-dev.service`로 실행
- 애플리케이션은 `127.0.0.1:8080`에서만 수신하고, Caddy가 외부 HTTPS 요청을 전달한다.

EC2는 1GiB 메모리이므로 서비스의 Java 힙 상한을 448MiB로 설정했다. Valkey는 루프백에서만 수신하고 메모리 상한을 64MiB로 설정했다. 개발 환경의 배치 예약 작업은 별도로 활성화하기 전까지 실행되지 않는다.

새 EC2를 준비할 때는 Java 21, PostgreSQL 16 클라이언트(`psql`, `pg_restore`), Valkey를 설치하고 `java -version`, `psql --version`, `valkey-cli ping`으로 확인한다. Valkey 설정에서는 루프백 바인딩과 보호 모드를 유지하고 메모리 상한을 설정한 뒤 서비스를 시작한다. PostgreSQL 서버와 Valkey의 외부 포트는 열지 않는다. EC2 보안 그룹의 SSH 22번은 관리자의 현재 IP에서만 허용한다.

## 비밀값과 DB 연결

`/etc/geupjido/dev.env`는 EC2에만 둔다. 소유자는 `root`, 그룹은 `geupjido`, 권한은 `0640`이다. 파일에는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 입력한다. `DB_URL`은 RDS CA 인증서를 이용한 `sslmode=verify-full` 연결을 사용한다. 브라우저의 교차 출처 요청이 필요하면 `WORLDCUP_CORS_ALLOWED_ORIGINS`에 허용할 출처를 정확히 지정한다. 형식은 [`dev.env.example`](../../scripts/deploy/dev.env.example)을 참고하고, 실제 호스트·비밀번호·환경 변수 값은 저장소에 기록하지 않는다.

서비스 파일과 설치 스크립트는 [`scripts/deploy/`](../../scripts/deploy/)에 있다. 서비스 설치는 JAR와 RDS CA 인증서를 EC2에 전송한 뒤 다음처럼 수행한다.

```bash
bash install-dev-service.sh geupjido-backend-0.0.1-SNAPSHOT.jar rds-global-bundle.pem
sudo nano /etc/geupjido/dev.env
sudo systemctl enable --now geupjido-dev.service
curl -fsS http://127.0.0.1:8080/actuator/health
```

비밀번호는 EC2의 편집기 안에서만 입력한다. 명령행 인수, Git, 채팅, 로그에 적지 않는다. 설치 스크립트는 서비스 파일을 설치하지만 비밀번호를 입력하거나 서비스를 시작하지 않는다.

## 초기 데이터 이전

1. 빈 RDS에서 앱을 한 번 시작해 Flyway 마이그레이션을 적용한다.
2. 앱을 중지한다.
3. 로컬 PostgreSQL에서 `--data-only --no-owner --no-privileges -Fc`로 백업한 9개 테이블을 전송한다.
4. [`restore-dev-data.py`](../../scripts/deploy/restore-dev-data.py)를 실행한다. 스크립트는 대상 테이블이 비어 있는지 확인하고, 단일 트랜잭션에서 복원하며, 복원 후 건수를 출력한다.
5. 앱을 다시 시작하고 health 및 실제 조회 API를 확인한다.

복원 스크립트는 EC2에서 `python3 restore-dev-data.py /etc/geupjido/dev.env /path/to/data.dump` 형식으로 실행한다. 환경 파일의 읽기 권한이 필요한 경우 `sudo`로 실행한다. 대상 9개 테이블 중 하나라도 행이 있으면 시작 전에 중단하므로, 이 절차는 **빈 개발 DB의 최초 적재**에만 사용한다. 기존 개발 DB를 갱신하거나 복구할 때는 별도 백업과 계획을 먼저 마련한다.

옮긴 테이블은 `region`, `city`, `zone`, `zone_adjacency`, `complex`, `apartment_trade`, `apartment_presale_trade`, `apartment_trade_conflict_seen`, `apartment_trade_conflict`이다. 로컬 테스트용 월드컵 게임·투표·건너뛰기 기록, Flyway 이력, PostGIS 시스템 데이터는 이전하지 않는다. 새 DB의 스키마와 기본 행은 Flyway가 만든다.

첫 이전에서 Flyway 27개가 모두 적용되었고, 복원 후 지역 3개·시군구 62개·권역 282개·단지 5,610개·매매 거래 433,635개가 원본과 일치했다. 개발 서버 내부의 `/actuator/health`는 `UP`, `/api/v1/worldcup/zones`는 HTTP 200을 반환했다.

## 외부 HTTPS 연결

EC2에 탄력적 IP를 연결하고 DNS의 `dev-api` A 레코드를 그 IP로 설정한다. EC2 보안 그룹은 외부 IPv4의 HTTP 80번과 HTTPS 443번을 허용한다. 백엔드 8080번은 루프백에서만 수신한다.

[`install-dev-proxy.sh`](../../scripts/deploy/install-dev-proxy.sh)는 공식 Caddy ARM64 릴리스의 SHA-512를 확인한 뒤 바이너리와 Caddyfile, systemd 서비스를 설치한다. Caddy는 인증서를 자동 발급·갱신하고 HTTP 요청을 HTTPS로 돌린다. 설치 전에 DNS와 보안 그룹을 확인한다.

```bash
bash install-dev-proxy.sh
sudo systemctl status geupjido-dev-proxy.service
curl -fsS https://dev-api.geupjido.site/actuator/health
```

프록시 설정을 변경할 때는 `sudo /usr/local/bin/caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile`로 먼저 검증한 뒤 `sudo systemctl reload geupjido-dev-proxy.service`를 실행한다. 문제가 생기면 이전 Caddyfile을 복원하고 다시 검증·재시작한다. 인증서 파일은 Caddy의 `/var/lib/caddy`에 보관하므로 서비스 재시작 시 유지된다.

2026-11-13 검증에서 외부 HTTPS health와 권역 API가 HTTP 200, Swagger UI와 OpenAPI JSON이 HTTP 200, HTTP 접속이 HTTPS로 308 리다이렉트됐다. Let's Encrypt 인증서가 발급됐고, HTTPS에서 익명 쿠키로 게임을 시작한 뒤 동일한 10판을 다시 조회했다(시작 201, 재조회 200).

로컬 프런트엔드는 현재 Vite의 `/api` 프록시가 `http://localhost:8080`을 향한다. 프런트엔드 연결 규약과 쿠키·CORS 검증 범위는 [개발 API 프런트엔드 연동](dev-api-frontend-handoff.md)에 기록한다.

## 새 버전 배포와 롤백

개발 서버에서는 `develop`의 검증된 커밋을 로컬에서 `./gradlew clean build`로 빌드한다. EC2의 메모리가 1GiB이므로 서버에서 Gradle 빌드를 실행하지 않는다. 빌드된 실행 JAR와 [`release-dev-service.sh`](../../scripts/deploy/release-dev-service.sh)를 EC2 사용자 홈으로 전송한다. `SSH_KEY_PATH`에는 로컬 키 파일 경로를 입력하고, 처음 연결할 때는 서버 호스트 키를 확인한다.

```bash
SSH_KEY_PATH=/path/to/private-key.pem
EC2_TARGET=ec2-user@dev-api.geupjido.site
./gradlew clean build
scp -i "$SSH_KEY_PATH" scripts/deploy/release-dev-service.sh \
  build/libs/geupjido-backend-0.0.1-SNAPSHOT.jar \
  "$EC2_TARGET":/home/ec2-user/
ssh -i "$SSH_KEY_PATH" "$EC2_TARGET" \
  'sudo bash /home/ec2-user/release-dev-service.sh deploy /home/ec2-user/geupjido-backend-0.0.1-SNAPSHOT.jar'
curl -fsS https://dev-api.geupjido.site/actuator/health
```

스크립트는 기존 JAR를 `/opt/geupjido/releases/`에 보존하고 새 JAR를 별도 파일로 설치한다. `geupjido-backend.jar` 링크를 원자적으로 바꾼 뒤 서비스를 재시작하고 내부 health가 `UP`인지 최대 약 1분 동안 확인한다. 실패하면 이전 JAR 링크를 복구하고 서비스를 다시 시작한다. 성공한 배포의 이전 버전은 `previous.jar` 링크로 가리킨다.

성공한 배포를 수동으로 되돌릴 때는 다음 명령을 사용한다. 롤백도 같은 health 검사를 거치며, 실패하면 롤백 전 버전으로 되돌린다.

```bash
ssh -i "$SSH_KEY_PATH" "$EC2_TARGET" \
  'sudo bash /home/ec2-user/release-dev-service.sh rollback'
curl -fsS https://dev-api.geupjido.site/actuator/health
```

이 절차는 애플리케이션 JAR만 되돌린다. 새 버전이 Flyway로 데이터베이스 스키마를 변경했다면 스키마는 자동으로 되돌아가지 않는다. 이전 버전과 호환되지 않는 스키마 변경은 배포 전에 별도 복구 절차를 정해야 한다.

EC2에서 서비스 상태와 최근 로그를 확인하거나 수동 재시작할 때는 다음 명령을 사용한다.

```bash
sudo systemctl status geupjido-dev.service geupjido-dev-proxy.service
sudo journalctl -u geupjido-dev.service -n 100 --no-pager
sudo systemctl restart geupjido-dev.service
```

2026-11-13 동일한 코드의 JAR로 배포와 수동 롤백을 실행하고 각 단계에서 내부·외부 health가 `UP`임을 확인했다. 실행할 수 없는 JAR로 실패 배포도 시험했으며, 스크립트가 이전 JAR 링크를 복구한 뒤 서비스 health `UP`을 확인했다.

## 비용과 데이터 복구

2026-11-13 서울 리전의 AWS Pricing Calculator에서 선택한 사양은 EC2 `t4g.micro` 1대와 gp3 20GiB, RDS PostgreSQL `db.t4g.micro` 단일 AZ 1대와 gp3 20GiB다. 24시간 가동하는 온디맨드 기준 계산기 금액은 각각 월 **$9.41**, **$20.87**, 합계 **$30.28**이다. EC2에 연결된 공개 IPv4 주소 한 개는 [AWS VPC 요금표](https://aws.amazon.com/vpc/pricing/)의 시간당 $0.005를 적용하면 730시간 기준 월 **약 $3.65**다. 따라서 현재 구성의 단순 합계는 월 **약 $33.93**이다. 실제 청구액은 무료 크레딧·적용 가능한 무료 혜택, 실행 시간, 데이터 전송량, 추가 백업 사용량, 세금에 따라 달라진다. 탄력적 IP는 기존 자동 할당 IPv4를 대체하며, 현재 EC2의 공개 IPv4 주소는 하나다.

RDS 자동 백업은 현재 AWS 계정의 무료 플랜에서 허용된 **1일 보존**으로 설정됐다. 생성 시 7일 보존은 계정 제한으로 거부됐다. 이 기간은 장기 복구 수단이 아니므로 대량 데이터 변경 전에는 별도 수동 스냅샷을 생성하고, 복원이 필요한 경우 스냅샷이나 지정 시점 복원으로 *새 DB 인스턴스*를 만든 뒤 데이터와 PostGIS를 확인하고 애플리케이션의 DB 연결 대상을 변경한다. 개발 데이터의 원본은 로컬 DB 백업과 재수집 경로에도 남겨 둔다. [AWS RDS 백업 안내](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html)에 따르면 계정·리전의 프로비저닝된 DB 스토리지와 같은 양까지 백업 스토리지 무료 할당량이 적용되고, 초과 사용량에는 요금이 붙는다. EC2의 배포 JAR는 위 롤백 절차로 복구하지만, 데이터베이스는 JAR 롤백만으로 복구되지 않는다.

## 이어서 확인할 것

- 프런트엔드 담당자의 실제 화면 연결 후 쿠키·투표 동작 확인. 개발 API를 이용한 Chrome 브라우저 검증은 [연동 문서](dev-api-frontend-handoff.md)에 기록했다.
- AWS 결제 화면에서 실제 사용량과 공개 IPv4·백업 비용 확인 및 비용 알림 설정
