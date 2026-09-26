# GCP 배포 스크립트

이 디렉터리는 PLIMAP 백엔드의 GCP dev·prod 배포 자동화 파일을 관리합니다. 실제 Cloud SQL, GCS, Redis, VPC, External Application Load Balancer와 DNS/TLS 리소스는 배포 스크립트와 별도 운영 절차로 관리합니다.

## 파일 구성

- `deploy-dev.ps1`: Cloud Run dev 서비스를 배포하고 health, Swagger UI, OpenAPI 응답을 검증합니다.
- `PROD_INFRASTRUCTURE.md`: 현재 Prod 인프라 기준과 신규 프로젝트·재해 복구 시의 승인형 재구축 절차를 정의합니다.
- `PROD_DATABASE.md`: PG17/PostGIS 검증 게이트와 Prod DB 역할 bootstrap 순서를 설명합니다.
- `configure-prod-database-grants.sql`: `plimap_migrator`가 Flyway 전에 실행해 이후 생성 객체의 runtime 기본 권한을 설정합니다.
- `grant-prod-database-existing-objects.sql`: 각 기존 객체 owner가 별도로 실행해 자신이 소유한 객체에 runtime 권한을 부여합니다.
- `bootstrap-prod-cloud-run.ps1`: 신규 프로젝트·재해 복구 시 LB 전용 ingress와 서비스 단위 IAM을 재구축합니다. 기본 실행은 plan-only이며 승인된 `-Apply`가 있어야 변경합니다.
- `deploy-prod.ps1`: Prod revision을 공개 traffic tag 없이 0%로 기동하고 Ready·image digest를 검증한 뒤 트래픽을 전환하며, LB 전용 상태 또는 선택적 공개 smoke 검증 실패 시 직전 revision을 복구합니다.
- `configure-prod-5xx-discord-alert.ps1`: Prod 애플리케이션 5xx 로그를 Discord로 전달하는 Logging Sink, Pub/Sub, Cloud Run Function과 Eventarc를 구성합니다. 기본 실행은 plan-only입니다.
- `send-prod-5xx-discord-test.ps1`: 같은 전달 경로를 확인하는 `[TEST]` 합성 로그 한 건을 기록합니다. 기본 실행은 plan-only입니다.
- `prod-5xx-discord/`: Python Cloud Run Function 소스와 단위 테스트를 보관합니다.
- `SECRETS.md`: 환경변수, GitHub Environment Variable, Secret Manager 매핑과 값 교체 방법을 설명합니다.

## 공통 준비

- Google Cloud CLI를 설치하고 `plimap` 프로젝트에 접근 가능한 계정으로 인증합니다.
- 배포할 컨테이너 이미지가 Artifact Registry에 존재해야 합니다.
- 환경별 Secret Manager 항목에 활성 버전이 있어야 합니다.
- Cloud Run runtime service account와 필요한 IAM 권한을 먼저 구성해야 합니다.

환경 설정과 Secret 준비 방법은 [SECRETS.md](SECRETS.md), 현재 인프라 기준과 승인형 재구축 절차는 [PROD_INFRASTRUCTURE.md](PROD_INFRASTRUCTURE.md), 전체 구성 설명은 [Deployment Guide](../../docs/DEPLOYMENT.md)를 참고합니다.

## Dev 실행

저장소 루트에서 다음 명령을 실행합니다.

```powershell
.\scripts\gcp\deploy-dev.ps1
```

GitHub Actions의 `Deploy Dev` 워크플로도 동일한 스크립트를 사용합니다.

## Prod 5xx Discord 알림

대상은 `GlobalExceptionHandler`가 처리하고 `HTTP_5XX` 구조화 로그를 남긴
`plimap-api-prod` 요청입니다. Cloud Run 플랫폼 또는 Load Balancer에서 애플리케이션에
도달하기 전에 발생한 5xx는 포함하지 않습니다.

운영자가 Discord `#백엔드-서버에서-알림` 채널의 Webhook을 만든 뒤 GCP Console에서
`plimap-prod-discord-webhook-url` Secret과 ENABLED 버전을 먼저 생성합니다. URL 값은
명령행, 문서, 이슈 또는 채팅에 입력하지 않습니다.

저장소 루트에서 변경 없는 계획을 먼저 확인합니다.

```powershell
.\scripts\gcp\configure-prod-5xx-discord-alert.ps1
```

계획과 Secret 준비 상태를 확인한 운영자가 별도 승인을 받은 뒤에만 리소스를 구성합니다.
스크립트는 Function 실행용 `plimap-prod-5xx-alert`와 소스 빌드용
`plimap-prod-5xx-build` 서비스 계정을 분리합니다. 빌드 계정에는 Cloud Run source
build에 필요한 `roles/run.builder`만 부여하고, `gcloud run deploy`에 해당 계정을
명시해 기본 Compute 서비스 계정에 빌드 권한을 추가하지 않습니다.

```powershell
.\scripts\gcp\configure-prod-5xx-discord-alert.ps1 -Apply
```

구성이 완료되면 테스트도 먼저 plan-only로 확인합니다.

```powershell
.\scripts\gcp\send-prod-5xx-discord-test.ps1
.\scripts\gcp\send-prod-5xx-discord-test.ps1 -Apply
```

`-Apply` 테스트는 `testEvent=true`인 Cloud Logging 항목 한 건을 기록합니다. Logging
Sink, Pub/Sub, Eventarc와 Function을 모두 통과한 메시지는 같은 Discord 채널에
`🧪 [TEST]` 제목으로 표시됩니다. 테스트 실행은 운영 배포와 분리하며, Function은
Discord 실패를 한 번 기록하고 재시도하거나 DLQ에 저장하지 않습니다.

로컬 정적 검증과 Python 단위 테스트는 다음 명령을 사용합니다.

```powershell
.\scripts\gcp\test-configure-prod-5xx-discord-alert.ps1
python -m pytest .\scripts\gcp\prod-5xx-discord\test_main.py
```

## Prod Cloud Run 재구축

`plimap-api-prod`의 최초 서비스·IAM bootstrap은 완료되어 일상 배포에서 이 스크립트를 실행하지 않습니다. 신규 프로젝트 또는 재해 복구로 서비스를 다시 만들 때만 인프라 관리자가 별도 승인을 받고 사용합니다.

먼저 `-Apply` 없이 충돌과 계획만 확인합니다.

```powershell
.\scripts\gcp\bootstrap-prod-cloud-run.ps1 `
  -VpcNetwork "<prod-vpc-network>" `
  -VpcSubnet "<prod-cloud-run-subnet>"
```

실제 `-Apply` 실행은 [PROD_INFRASTRUCTURE.md](PROD_INFRASTRUCTURE.md)의 재구축 게이트, 동명 리소스 충돌 검사와 명시적 승인을 모두 통과한 경우에만 수행합니다. 기존 운영 서비스에는 실행하지 않습니다. Cloud Run의 첫 revision 제약 때문에 재구축 시 sample bootstrap revision을 사용하되, 기본 `run.app` URL을 비활성화하고 ingress를 `internal-and-cloud-load-balancing`으로 제한합니다.

## Prod 실행

Prod는 GitHub Actions의 `Deploy Prod` 워크플로 사용을 원칙으로 합니다. `main` push의 `PLIMAP CI`가 성공하면 `prepare` Job이 배포 commit만 확정하고, `production` Environment의 필수 승인자가 **Approve and deploy**를 선택한 뒤에만 GCP 인증과 운영 설정 접근이 시작됩니다.

권한이 있는 운영자가 장애 대응이나 사전 검증을 위해 로컬에서 실행할 때는 immutable image digest, 40자리 commit SHA와 실제 인프라 이름을 명시합니다.

```powershell
.\scripts\gcp\deploy-prod.ps1 `
  -Image "asia-northeast3-docker.pkg.dev/plimap/plimap-docker/api@sha256:<digest>" `
  -DeployCommit "<40-character-commit-sha>" `
  -ProfileImageBucket "<prod-gcs-bucket>" `
  -VpcNetwork "<prod-vpc-network>" `
  -VpcSubnet "<prod-cloud-run-subnet>"
```

스크립트는 다음 순서로 동작합니다.

1. 입력값 형식, 승인된 Artifact Registry repository의 commit SHA image와 immutable digest 일치를 확인합니다.
2. Runtime service account, VPC/subnet, GCS bucket을 확인하고 각 Prod Secret의 `latest`가 가리키는 `ENABLED` 숫자 버전을 확정합니다.
3. 사전 생성된 서비스의 공개 Invoker, LB 전용 ingress와 기본 URL 비활성 상태를 확인한 뒤 새 revision을 공개 tag 없이 `--no-traffic`과 deploy health check로 기동합니다.
4. 후보 revision이 Ready이고 실제 resolved image digest가 승인된 digest와 일치하는지 확인합니다.
5. 후보 revision으로 트래픽을 100% 전환하고 실제 트래픽 상태가 단일 100%로 수렴하는지 확인합니다. 기본 URL은 계속 비활성화합니다.
6. `-PublicSmokeEnabled`가 켜진 경우 `https://plimap.kr`에서 프론트, CSRF 응답·cookie, Google OAuth 3xx·`Location`, Swagger/OpenAPI·Actuator 차단을 검증합니다.
7. 실패 시 실제 트래픽 상태를 다시 조회하고 직전 revision으로 100% 복구한 뒤 트래픽과 LB 전용 상태를 재검증합니다.

후보 revision에는 외부에서 호출할 수 있는 traffic tag URL을 만들지 않고 기본 URL도 계속 비활성화합니다. 실패 시 직전 애플리케이션 revision으로 복구합니다. Deploy health check가 후보 컨테이너를 시작하므로 Flyway는 트래픽 전환 전에도 운영 DB에 Migration을 적용할 수 있고, 애플리케이션 rollback은 적용된 Migration을 되돌리지 않습니다. 현재 DNS/TLS가 활성화되어 공개 smoke를 항상 수행하며, 재해 복구 중 DNS가 아직 연결되지 않은 예외 상황에서만 일시 비활성화합니다.
