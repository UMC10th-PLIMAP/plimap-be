# dev/prod 환경변수 및 Secret 관리

이 문서는 `deploy-dev.ps1`, `deploy-prod.ps1`과 각 GitHub Actions 워크플로가 Cloud Run에 전달하는 환경 설정을 관리합니다.

비밀값은 저장소, 문서, 채팅, 명령행 인자 또는 PowerShell history에 남기지 않고 GCP 프로젝트 `plimap`의 Secret Manager에서 관리합니다.

모든 `application*.yml`은 Git에 추적되는 공개 설정 파일입니다. 실제 값을 직접 작성하지 않고 환경변수 placeholder만 유지합니다.

## Dev 일반 환경변수

다음 값은 비밀정보가 아니며 `deploy-dev.ps1`이 배포 시 생성합니다.

| 애플리케이션 환경변수 | 생성 기준 | dev 기본값 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 스크립트 고정값 | `dev` |
| `PUBLIC_BASE_URL` | `PublicBaseUrl` | `https://dev.plimap.kr` |
| `CORS_ALLOWED_ORIGINS` | `CorsAllowedOrigins` | `https://dev.plimap.kr,https://admin.plimap.kr,http://localhost:5173,http://192.168.*:[*],https://192.168.*:[*],https://pr-*.plimap.kr` |
| `OAUTH_REDIRECT_URI` | 기본 `FrontendRedirectUri` 또는 `PublicBaseUrl` + `/app/oauth/callback` | `https://dev.plimap.kr/app/oauth/callback` |
| `OAUTH_ALLOWED_FRONTEND_ORIGINS` | `OAuthAllowedFrontendOrigins` | `https://dev.plimap.kr,https://admin.plimap.kr,http://localhost:5173,https://pr-*.plimap.kr` |
| `KAKAO_REDIRECT_URI` | `PublicBaseUrl` + callback 경로 | `https://dev.plimap.kr/oauth/callback/kakao` |
| `GOOGLE_REDIRECT_URI` | `PublicBaseUrl` + callback 경로 | `https://dev.plimap.kr/oauth/callback/google` |
| `PROFILE_IMAGE_STORAGE_PROVIDER` | 스크립트 고정값 | `supabase` |
| `PROFILE_IMAGE_BUCKET` | `ProfileImageBucket` | `profile-images` |

When `DEV_CORS_ALLOWED_ORIGINS` is not provided, Dev deployments include the Spring CORS patterns `http://192.168.*:[*]`, `https://192.168.*:[*]`, and `https://pr-*.plimap.kr` in the default CORS allowlist. Explicit entries are preserved, and the preview pattern is appended to both the CORS and OAuth frontend allowlists.

GitHub Actions에서는 다음 Repository Variable로 공개 주소와 allowlist를 덮어쓸 수 있습니다. `DEV_PUBLIC_BASE_URL`이 없으면 스크립트의 dev 기본값을 사용하고, `DEV_FRONTEND_REDIRECT_URI`가 없으면 선택된 공개 origin에 `/app/oauth/callback`을 붙여 기본 로그인 완료 주소를 생성합니다. CORS와 OAuth 프론트 allowlist가 없으면 Dev 배포 프론트와 로컬 프론트 Origin을 모두 포함합니다.

| Repository Variable | 스크립트 인자 | dev 기본 동작 |
| --- | --- | --- |
| `DEV_PUBLIC_BASE_URL` | `PublicBaseUrl` | `https://dev.plimap.kr` |
| `DEV_FRONTEND_REDIRECT_URI` | `FrontendRedirectUri` | 미설정 시 `PublicBaseUrl` + `/app/oauth/callback` |
| `DEV_CORS_ALLOWED_ORIGINS` | `CorsAllowedOrigins` | 미설정 시 `PublicBaseUrl,https://admin.plimap.kr,http://localhost:5173,http://192.168.*:[*],https://192.168.*:[*],https://pr-*.plimap.kr` |
| `DEV_OAUTH_ALLOWED_FRONTEND_ORIGINS` | `OAuthAllowedFrontendOrigins` | 미설정 시 `PublicBaseUrl,https://admin.plimap.kr,http://localhost:5173,https://pr-*.plimap.kr` |

`PublicBaseUrl`은 경로, query, fragment, credentials, custom port가 없는 HTTPS Origin이어야 합니다. `FrontendRedirectUri`는 `frontendOrigin`이 없거나 저장된 값이 유효하지 않을 때 사용하는 안전한 기본 주소이므로 HTTPS와 `PublicBaseUrl` 동일 origin 조건을 유지합니다.

CORS와 OAuth 프론트 allowlist는 쉼표로 Origin을 구분합니다. CORS는 HTTPS Origin, HTTP localhost Origin, 그리고 `192.168.0.0/16` private-network Origin pattern을 허용하며 경로, query, fragment, credentials는 허용하지 않습니다. OAuth 프론트 allowlist는 exact Origin과 승인된 preview 패턴 `https://pr-*.plimap.kr`을 허용하고 두 allowlist에는 반드시 `PublicBaseUrl`이 포함되어야 합니다. OAuth 로그인 시작 시 전달된 `frontendOrigin`이 allowlist에 없으면 요청을 거부합니다.

## Dev Secret Manager 매핑

다음 값은 `deploy-dev.ps1`이 Secret Manager의 `latest` 활성 버전에서 주입합니다.

| 애플리케이션 환경변수 | Secret Manager ID |
| --- | --- |
| `DB_URL` | `plimap-dev-db-url` |
| `DB_USERNAME` | `plimap-dev-db-username` |
| `DB_PASSWORD` | `plimap-dev-db-password` |
| `REDIS_URL` | `plimap-dev-redis-url` |
| `JWT_SECRET` | `plimap-dev-jwt-secret` |
| `TEST_TOKEN_ISSUE_KEY` | `plimap-dev-test-token-issue-key` |
| `KAKAO_REST_API_KEY` | `plimap-dev-kakao-rest-api-key` |
| `KAKAO_REST_API_SECRET` | `plimap-dev-kakao-rest-api-secret` |
| `GOOGLE_CLIENT_ID` | `plimap-dev-google-client-id` |
| `GOOGLE_CLIENT_SECRET` | `plimap-dev-google-client-secret` |
| `YOUTUBE_API_KEY` | `plimap-dev-youtube-api-key` |
| `SUPABASE_URL` | `plimap-dev-supabase-url` |
| `SUPABASE_SECRET_KEY` | `plimap-dev-supabase-secret-key` |

## Dev 최초 입력 또는 값 교체

1. GCP Console에서 프로젝트 `plimap`을 선택하고 **Secret Manager**로 이동합니다.
2. 최초 입력이라면 위 ID로 Secret을 만들고, 이미 존재한다면 해당 Secret을 엽니다.
3. **새 버전 추가**를 선택해 새 값을 입력하고 활성화합니다. 기존 활성 버전은 즉시 삭제하지 않습니다.
4. 다음 dev 배포를 실행하거나 GitHub Actions의 `Deploy Dev`를 수동 실행합니다.

Cloud Run은 새 revision이 시작될 때 `latest` Secret 버전을 주입받으므로 값을 바꾼 뒤에는 재배포가 필요합니다. Secret 교체만을 위해 `develop`에 빈 커밋을 만들 필요는 없습니다.

### Dev 테스트 토큰 발급 키 관리

테스트 토큰 발급 키는 JWT 서명 키와 분리하고 암호학적으로 안전한 32바이트 이상의 난수로 생성합니다. 키를 표준 출력에 표시하는 생성 예시는 문서에 두지 않습니다. 승인된 비밀 생성 도구와 노출되지 않는 입력 경로를 사용해 `plimap-dev-test-token-issue-key`의 새 Secret 버전으로 바로 저장합니다.

발급 키는 저장소, 이슈, 채팅, 명령행 인자·history, CI 로그와 Swagger 캡처에 남기지 않습니다. Dev 토큰 발급 시에만 별도 보안 채널로 전달하고, 노출이 의심되면 Secret 버전을 교체한 뒤 Dev를 재배포합니다. Local profile은 발급 키를 요구하지 않으므로 외부 서버에 배포하지 않습니다.

## Dev 연결 정보 형식

### Supabase DB

dev는 Supabase **Transaction Pooler**를 사용합니다. `DB_URL`에는 비밀번호를 넣지 않고 `DB_USERNAME`과 `DB_PASSWORD`를 별도 Secret으로 저장합니다.

```text
DB_URL=jdbc:postgresql://{host}:6543/postgres?sslmode=require
```

### Redis Cloud

Dev는 공급자가 발급한 연결 URI 전체를 `plimap-dev-redis-url`에 저장합니다. 문서에서 실제 scheme, host, port 또는 credential을 재현하지 않으며 Prod Secret과 값을 공유하지 않습니다. TLS endpoint를 사용할 수 있으면 `rediss://`를 우선하고, 공급자 제약으로 비-TLS endpoint를 쓰는 Dev 구성에는 운영·민감 데이터를 저장하지 않습니다. 비밀번호의 예약 문자는 URL 인코딩합니다.

### OAuth

Kakao와 Google에는 dev 전용 OAuth client를 사용합니다. 각 Provider Console의 callback URI는 일반 환경변수 표의 `KAKAO_REDIRECT_URI`, `GOOGLE_REDIRECT_URI`와 일치해야 합니다. Cloud Run 원본 URL과 `localhost:5173`은 Provider callback URI로 사용하지 않습니다. 백엔드 callback 처리 후에는 로그인 시작 요청에 저장된 `frontendOrigin`에 따라 Dev 배포 프론트 또는 로컬 프론트의 `/app/oauth/callback`으로 이동합니다.

---

## Prod GitHub Environment Variable

다음 값은 GitHub `production` Environment의 Variable로 관리합니다. 비밀값은 아니지만 승인된 Prod Job에서만 읽도록 workflow를 구성합니다. 인프라 식별자는 현재 운영 리소스와 일치해야 합니다.

| Environment Variable | 스크립트 인자 | 기본값 또는 조건 |
| --- | --- | --- |
| `PROD_PUBLIC_BASE_URL` | `PublicBaseUrl` | 미설정 시 `https://plimap.kr` |
| `PROD_FRONTEND_REDIRECT_URI` | `FrontendRedirectUri` | 미설정 시 `PublicBaseUrl` + `/app/oauth/callback` |
| `PROD_CORS_ALLOWED_ORIGINS` | `CorsAllowedOrigins` | 명시한 Origin을 유지하고 `PublicBaseUrl,https://admin.plimap.kr`을 항상 포함 |
| `PROD_OAUTH_ALLOWED_FRONTEND_ORIGINS` | `OAuthAllowedFrontendOrigins` | 명시한 Origin을 유지하고 `PublicBaseUrl,https://admin.plimap.kr`을 항상 포함 |
| `PROD_GCS_BUCKET` | `ProfileImageBucket` | 필수, 전역에서 고유한 Prod bucket 이름 |
| `PROD_VPC_NETWORK` | `VpcNetwork` | 필수, Direct VPC egress 대상 network |
| `PROD_VPC_SUBNET` | `VpcSubnet` | 필수, `asia-northeast3` Cloud Run 전용 subnet |
| `PROD_PUBLIC_SMOKE_ENABLED` | `PublicSmokeEnabled` | 운영값 `true`; 재해 복구 중 DNS 미연결 시에만 일시 `false` |

`deploy-prod.ps1`은 다음 공개 애플리케이션 환경변수를 생성합니다.

| 애플리케이션 환경변수 | Prod 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PUBLIC_BASE_URL` | `https://plimap.kr` 또는 승인된 override |
| `CORS_ALLOWED_ORIGINS` | 최소 `PublicBaseUrl,https://admin.plimap.kr` |
| `OAUTH_REDIRECT_URI` | 기본 `https://plimap.kr/app/oauth/callback` |
| `OAUTH_ALLOWED_FRONTEND_ORIGINS` | 최소 `PublicBaseUrl,https://admin.plimap.kr` |
| `KAKAO_REDIRECT_URI` | `PublicBaseUrl` + `/oauth/callback/kakao` |
| `GOOGLE_REDIRECT_URI` | `PublicBaseUrl` + `/oauth/callback/google` |
| `PROFILE_IMAGE_BUCKET` | `PROD_GCS_BUCKET` |
| `PROFILE_IMAGE_PUBLIC_BASE_URL` | `https://storage.googleapis.com` |

`https://admin.plimap.kr`은 Prod 데이터를 관리하는 전용 프론트 Origin이므로 두 allowlist에서 제거할 수 없습니다. 배포 스크립트는 GitHub Environment Variable에 저장된 추가 Origin을 보존하면서 Prod 공개 Origin과 Admin Origin을 병합하고 중복을 제거합니다.

## Prod Secret Manager 매핑

Prod Secret은 Dev와 별도 ID와 값을 사용합니다. `deploy-prod.ps1`은 다음 Secret의 `latest` 버전 metadata를 조회해 상태가 `ENABLED`인지 확인한 뒤, 그 시점의 숫자 버전을 환경변수에 고정합니다. Secret payload는 읽거나 출력하지 않습니다.

| 애플리케이션 환경변수 | Secret Manager ID |
| --- | --- |
| `DB_URL` | `plimap-prod-db-url` |
| `DB_USERNAME` | `plimap-prod-db-username` |
| `DB_PASSWORD` | `plimap-prod-db-password` |
| `FLYWAY_USERNAME` | `plimap-prod-flyway-username` |
| `FLYWAY_PASSWORD` | `plimap-prod-flyway-password` |
| `REDIS_URL` | `plimap-prod-redis-url` |
| `JWT_SECRET` | `plimap-prod-jwt-secret` |
| `KAKAO_REST_API_KEY` | `plimap-prod-kakao-rest-api-key` |
| `KAKAO_REST_API_SECRET` | `plimap-prod-kakao-rest-api-secret` |
| `GOOGLE_CLIENT_ID` | `plimap-prod-google-client-id` |
| `GOOGLE_CLIENT_SECRET` | `plimap-prod-google-client-secret` |
| `YOUTUBE_API_KEY` | `plimap-prod-youtube-api-key` |

배포 결과와 Actions Summary에는 Secret ID와 선택된 숫자 버전만 기록합니다. 새 Secret 버전을 만든 뒤에는 다시 배포해야 새 revision이 해당 버전을 사용하며, 기존 revision과 rollback 대상 revision은 자신에게 고정된 버전을 계속 사용합니다.

Prod에는 Dev 테스트 토큰 Secret과 Supabase Secret을 주입하지 않습니다. GCS 인증은 `plimap-api-prod@plimap.iam.gserviceaccount.com`의 Application Default Credentials를 사용합니다.

## Prod 연결 정보 형식

### Cloud SQL PostgreSQL/PostGIS

Cloud Run은 Direct VPC egress의 `private-ranges-only` 경로로 Cloud SQL private IP에 직접 연결합니다. 따라서 영구 Cloud SQL 인스턴스의 connector enforcement는 `NOT_REQUIRED`로 유지합니다. `REQUIRED`는 Cloud SQL Auth Proxy 또는 Language Connector가 아닌 직접 DB 연결을 거부합니다. DB 비밀번호는 JDBC URL에 포함하지 않습니다.

```text
DB_URL=jdbc:postgresql://{cloud-sql-private-ip}:5432/{database}?sslmode=require
DB_USERNAME={prod-database-user}
DB_PASSWORD={prod-database-password}
```

Prod HikariCP는 `maximum-pool-size=8`, `minimum-idle=0`, `connection-timeout=5000ms`를 사용합니다. Cloud Run 서비스 최대 3개 인스턴스 기준 애플리케이션 최대 연결은 24개입니다.
Flyway는 `FLYWAY_USERNAME`, `FLYWAY_PASSWORD`로 Migration 전용 DB 사용자를 사용하며, 애플리케이션 runtime의 `DB_USERNAME`, `DB_PASSWORD`와 분리합니다.

Prod의 영구 Cloud SQL은 PostgreSQL 17과 PostGIS 3.5.2로 운영 중입니다. `bootstrap-prod-database.sql`과 권한 SQL은 신규 인스턴스 또는 재해 복구 재구축에서만 [PROD_DATABASE.md](PROD_DATABASE.md)의 승인된 순서로 실행합니다. `FLYWAY_USERNAME`과 `FLYWAY_PASSWORD`는 runtime 계정과 분리하고 Flyway 계정을 승격하지 않습니다. Runtime sequence 권한은 `USAGE`로 제한합니다.

### Prod Redis

Dev 무료 Redis와 데이터를 섞지 않도록 Prod 전용 Redis Cloud database/instance의 TLS URL을 저장합니다.

```text
REDIS_URL=rediss://default:{url-encoded-password}@{host}:{port}
```

애플리케이션 기동 시 Prod는 `rediss://` TLS URL, Cloud SQL private IP의 PostgreSQL JDBC DB URL, `plimap_app` runtime 사용자, `plimap_migrator` Flyway 사용자와 32바이트 이상의 JWT Secret을 검증합니다. DB URL은 기본 5432 port와 `sslmode=require`를 사용해야 하며 loopback, custom port, 사용자·비밀번호 포함 URL을 거부합니다. 검증 오류에는 실제 설정값을 출력하지 않습니다.

### Prod OAuth

Kakao와 Google에는 Prod 전용 OAuth client를 사용하고 callback URI를 `https://plimap.kr/oauth/callback/{provider}`와 일치시킵니다. 운영 client secret을 Dev Secret에 재사용하지 않습니다.

## Prod IAM 최소 권한

- Runtime service account `plimap-api-prod@plimap.iam.gserviceaccount.com`: Prod Secret에 대한 `roles/secretmanager.secretAccessor`, Prod bucket에 대한 `roles/storage.objectUser`
- GitHub deployer: Cloud Run 배포, Artifact Registry push, runtime service account 사용, Direct VPC egress 설정과 Prod Secret version metadata 조회(`secretmanager.versions.get`)에 필요한 권한. Secret payload 접근 권한은 배포 검증에 필요하지 않음
- Cloud Run 재구축 관리자: 신규 프로젝트 또는 재해 복구 시에만 승인 후 bootstrap을 수행하고, 기본 URL 비활성화와 `internal-and-cloud-load-balancing` ingress를 적용. 반복 배포에는 관리자 권한을 사용하지 않음
- 공개 프로필 이미지 bucket: uniform bucket-level access 사용, runtime service account만 쓰기·삭제, `allUsers`에는 객체 GET 전용 `roles/storage.legacyObjectReader`만 부여해 목록 조회를 허용하지 않음

현재 bucket은 공개 객체 GET과 목록 차단 정책을 사용합니다. Public Access Prevention 조직 정책이 적용되면 공개 URL 방식이 동작하지 않으므로 정책을 우회하지 않고 signed URL 또는 애플리케이션 proxy 방식으로 재설계합니다. Object Versioning과 7일 Soft Delete 정책 변경, 값 입력과 IAM 변경은 별도 승인 후 Secret payload를 노출하지 않는 GCP Console 또는 표준 입력 기반 절차로 수행합니다.
