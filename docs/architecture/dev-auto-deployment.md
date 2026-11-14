# 개발 서버 자동 배포

관련 이슈: [#163](https://github.com/sugkamoney/geupjido-backend/issues/163)

## 흐름

`main`에 PR이 병합되면 [CI](../../.github/workflows/ci.yml)가 Java 21로 빌드·테스트한다. 성공한 실행에서만 JAR와 [릴리스 스크립트](../../scripts/deploy/release-dev-service.sh)를 GitHub Actions 아티팩트로 저장하고, 별도 배포 작업이 같은 실행의 아티팩트를 내려받는다. PR 검사와 `develop` 푸시에서는 배포하지 않는다. `main` 실행은 진행 중인 배포를 취소하지 않는다. 여러 병합이 빠르게 이어지면 GitHub의 기본 동시성 규칙에 따라 대기 중인 오래된 실행은 최신 대기 실행으로 대체될 수 있다. [GitHub 동시성 안내](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)

배포 작업은 GitHub OIDC로 AWS 임시 자격 증명을 얻어 사설 S3 버킷의 `dev-releases/<커밋 SHA>/`에 두 파일을 올린다. Systems Manager Run Command는 개발 EC2에서 파일을 받아 SHA-256을 확인하고 기존 릴리스 스크립트로 교체한다. EC2에서는 빌드하지 않는다. 내부 health 실패 시 릴리스 스크립트가 이전 JAR로 복구한다. Run Command 성공 뒤 GitHub 실행 환경에서 외부 HTTPS health와 OpenAPI JSON을 확인하고, 이 검증이 실패하면 Run Command로 롤백한다. 배포 실패는 Actions 작업 실패로 남는다.

DB 비밀번호는 기존처럼 EC2의 `/etc/geupjido/dev.env`에만 둔다. GitHub에는 SSH 개인 키와 AWS 장기 액세스 키를 저장하지 않는다. Systems Manager를 사용하므로 배포를 위해 SSH 22번을 넓게 열지 않는다. 사설 S3 버킷의 객체 저장과 요청에는 사용량에 따른 요금이 생길 수 있으므로 `dev-releases/` 객체를 7일 뒤 삭제하는 수명 주기 규칙을 둔다. EC2용 Run Command에는 별도 사용료가 없다. [GitHub OIDC](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws), [AWS Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html)

S3는 CI 실행 장소가 아니라 **빌드된 JAR를 EC2로 전달하는 임시 보관소**다. GitHub Actions만으로도 SSH/SCP 배포는 가능하지만, 현재 EC2는 관리자 IP만 SSH로 허용한다. 표준 GitHub 호스팅 실행기의 IP는 작업마다 바뀌므로 직접 SSH를 쓰려면 SSH 키를 GitHub에 보관하고 인바운드 규칙을 조정해야 한다. EC2에 셀프 호스팅 실행기를 두는 방식은 1GiB 서버의 메모리를 더 사용한다. 현재의 제한된 SSH 설정과 작은 서버를 유지하기 위해 S3와 Systems Manager를 선택했다. [GitHub 호스팅 실행기 네트워크 안내](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)

## 최초 AWS 설정

이 설정이 끝나기 전에는 `main`에 병합해도 배포 작업이 실패한다. 따라서 첫 자동 배포 전에 다음을 준비하고 수동 배포로 검증한다.

1. 서울 리전에 **사설 S3 버킷**을 만들고 퍼블릭 액세스를 모두 차단한다. `dev-releases/` 접두사의 객체를 7일 뒤 삭제하는 수명 주기 규칙을 둔다. 버킷 이름을 아래 정책과 GitHub 변수에 동일하게 입력한다.
2. EC2에서 `amazon-ssm-agent`와 `aws --version`을 확인한다. 인스턴스 프로파일에 `AmazonSSMManagedInstanceCore`와 아래 S3 읽기 정책을 연결한다. EC2가 인터넷으로 HTTPS 443에 나갈 수 있어야 한다. Systems Manager의 관리형 노드 목록에서 인스턴스가 온라인인지 확인한다.
3. IAM에 GitHub OIDC 공급자 `token.actions.githubusercontent.com`을 등록하고, audience를 `sts.amazonaws.com`으로 설정한다. 아래 신뢰 정책의 저장소와 **`main` 브랜치** 조건을 가진 배포 역할을 만든다. 이 역할에 아래 S3 쓰기·SSM 정책을 붙인다.
4. GitHub 저장소의 **Settings → Secrets and variables → Actions → Variables**에 `AWS_DEPLOY_ROLE_ARN`, `AWS_DEPLOY_BUCKET`, `AWS_DEV_INSTANCE_ID`를 설정한다. 이 값은 비밀번호가 아니지만 저장소 파일에는 실제 값을 쓰지 않는다.
5. EC2에서 기존 [수동 배포 절차](dev-server-deployment.md#새-버전-배포와-롤백)를 한 번 확인하고, 작은 변경을 `main`에 병합해 `Build and Test` 다음 `Deploy development`가 성공하는지 확인한다.

EC2 역할의 추가 S3 읽기 정책 예시:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::<개발-배포-버킷>/dev-releases/*"
  }]
}
```

GitHub OIDC 배포 역할의 신뢰 정책 예시:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::<AWS-계정-ID>:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:sugkamoney@328667999/geupjido-backend@1368231278:ref:refs/heads/main"
      }
    }
  }]
}
```

이 저장소는 2026년 7월 15일 이후 생성되어 GitHub OIDC의 불변 `sub` 형식에 소유자 ID와 저장소 ID가 포함된다. IAM 역할 생성 화면에서 생성된 이름 기반 신뢰 정책은 역할 생성 후 위 값으로 수정한다. [GitHub OIDC 참조](https://docs.github.com/en/actions/reference/security/oidc)

GitHub OIDC 배포 역할의 권한 정책 예시:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<개발-배포-버킷>/dev-releases/*"
    },
    {
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript",
        "arn:aws:ec2:ap-northeast-2:<AWS-계정-ID>:instance/<개발-EC2-인스턴스-ID>"
      ]
    },
    {
      "Effect": "Allow",
      "Action": "ssm:GetCommandInvocation",
      "Resource": "*"
    }
  ]
}
```

`ssm:SendCommand`는 대상 서버에서 높은 권한으로 명령을 실행할 수 있으므로 역할 신뢰 조건과 대상 인스턴스 제한을 유지한다. [AWS Systems Manager IAM 정책 안내](https://docs.aws.amazon.com/systems-manager/latest/userguide/security_iam_id-based-policy-examples.html)

## 실패와 수동 복구

- GitHub Actions의 `Deploy development` 로그에서 SSM 명령 ID와 성공·실패 출력을 확인한다. 명령 실행 전 실패라면 EC2의 현재 JAR는 바뀌지 않는다.
- 새 JAR의 내부 health 실패는 릴리스 스크립트가 이전 JAR로 복구한다. 외부 HTTPS 검증 실패 시에는 별도 SSM 롤백을 실행하고 자동 배포를 실패 처리한다.
- 자동 롤백도 실패하면 [수동 롤백 절차](dev-server-deployment.md#새-버전-배포와-롤백)로 이전 JAR를 복구하고 서비스 로그를 확인한다.
- Flyway 마이그레이션이 DB 스키마를 변경한 경우 JAR 롤백만으로 DB가 되돌아가지는 않는다. 이전 버전과 호환되지 않는 스키마 변경은 별도 배포 계획과 DB 복구 절차가 필요하다.
