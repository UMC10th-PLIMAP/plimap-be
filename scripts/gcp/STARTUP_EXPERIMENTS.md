# Startup 검사·Spring AOT·AppCDS 비교 실험

## 무엇을 비교하는가

한 번의 코드 릴리스로 두 실험을 준비하되, **같은 이미지에서 설정을 하나씩 바꿔 비교**합니다. 설정 변경 시 Cloud Run revision은 새로 만들어집니다. 이미지 재빌드는 필요하지 않습니다.

| 모드 | AppCDS | Spring AOT | startup 검사 | 확인할 가설 |
| --- | --- | --- | --- | --- |
| `baseline` | OFF | OFF | 기존 5초 간격 | 이번 이미지의 비교 기준 |
| `startup` | OFF | OFF | 4초 간격 | 서버 준비 후 플랫폼이 준비 완료를 확인하는 대기를 줄이는가 |
| `aot` | OFF | ON | 기존 5초 간격 | 실행 시 Spring 설정을 분석하고 객체 생성 방법을 찾는 작업을 줄이는가 |
| `aot-quick` | OFF | ON | 3초 간격 | AOT의 내부 준비 단축이 검사 확인·화면 단축으로 이어지는가 |
| `combined` | ON | ON | 3초 간격 | 세 설정을 함께 적용한 결과는 같은 이미지의 baseline보다 빠른가 |
| `combined-baseline` | ON | ON | 기존 5초 간격 | 필요 시 조합을 기존 검사 정책으로 확인하는 명시적 선택 |

startup 변경은 그림의 ⑦ 애플리케이션 초기화가 빨라지는 기술이 아닙니다. 준비 완료를 확인하고 ⑧ 요청 처리 가능한 상태로 전환하는 시점이 앞당겨지는지 보는 실험입니다. AOT는 ⑦의 일부 작업을 빌드 시점에 미리 처리합니다. DB 연결·Flyway·실제 객체 생성은 런타임에도 수행합니다.

기존 모니터링에서 서버 READY 이후 startup 검사 성공까지 대기가 관측됐고, 설정 클래스 분석에도 시간이 기록됐습니다. 이는 후보 선정 근거이며, 해당 시간이 전부 줄어든다는 증거는 아닙니다. readiness 검사 등 다른 조건도 최종 요청 전달에 관여하므로 실제 화면까지 다시 측정합니다.

## 바꾸는 설정과 유지하는 조건

- startup 경로는 모든 모드에서 `/actuator/health:8080`, 초기 지연 0초, 검사 timeout 3초입니다.
- `baseline`: `periodSeconds=5`, `failureThreshold=24`.
- `short`: `periodSeconds=4`, `failureThreshold=30`. 두 설정의 명목 시작 허용 시간은 `period × failureThreshold = 120초`입니다. 4초는 timeout 3초보다 길게 두면서 검사 간격만 줄인 첫 후보입니다.
- `quick`: `periodSeconds=3`, `failureThreshold=40`. 기존 두 정책을 보존하며 동일한 명목 기동 허용 시간 120초를 유지합니다. 간격만 3초로 바꾸고 24회를 유지하면 허용 시간이 72초로 줄어 다른 실험이 됩니다.
- readiness·liveness, CPU·메모리, 최소 인스턴스, 네트워크, 인증·공개 경로, DB 설정은 실험 전환 스크립트에서 변경하지 않습니다. health에서 DB·Redis 검사를 끄지도 않습니다.
- 모든 비교에서 `PLIMAP_STARTUP_MONITORING_ENABLED=true`를 유지합니다. 초기 단독 비교 모드는 AppCDS OFF를 보존하고, 후속 조합 모드에서만 AppCDS·AOT를 함께 켭니다. 기본값은 기존과 동일하며 조합은 명시적으로 선택합니다.

이전 AppCDS 비교 이미지에는 이번 AOT 생성물과 코드 변경이 없습니다. **이전 결과를 이번 이미지의 대조군으로 재사용하지 않고 새 baseline을 측정**합니다.

## 한 번의 릴리스 준비

1. PR을 `develop`에 병합하고 기존 릴리스 절차로 `main`에 반영합니다. main 배포 승인은 운영자가 수행합니다.
2. GitHub `production` Environment Variables를 다음으로 둡니다. 실제 설정값은 배포 전에 확인합니다.

   | 변수 | 최초 비교 배포 값 |
   | --- | --- |
   | `PROD_APP_CDS_ENABLED` | `false` |
   | `PROD_STARTUP_MONITORING_ENABLED` | `true` |
   | `PROD_SPRING_AOT_ENABLED` | `false` |
   | `PROD_STARTUP_PROBE_POLICY` | `baseline` |

3. 승인된 배포 결과에서 **실제 linux/amd64 실행 image digest**와 100% 트래픽 revision을 확보합니다. OCI index digest·태그를 대신 사용하지 않습니다.
4. 실제 로그의 `SPRING_AOT_RUNTIME enabled=false`, `APP_CDS_CONFIGURATION enabled=false`와 위 설정을 확인합니다. 배포 직후 시작은 자연 cold 표본에서 제외합니다.

기본 AOT 값은 OFF, startup 정책은 baseline입니다. Dev는 기존 AppCDS 기본값과 일반 JVM 실행을 유지합니다. AOT 생성물은 prod 전용이며 AOT ON에 `dev`, `test` 또는 추가 profile을 섞으면 시작을 거부합니다.

## 전환과 원복

PowerShell에서 저장소 루트를 기준으로 실행합니다. 아래 값은 **직전에 확인한 실제 배포값**으로 대체하고, 출력 파일은 매번 다른 경로를 사용합니다.

```powershell
$image = 'asia-northeast3-docker.pkg.dev/plimap/plimap-docker/api@sha256:<실제 64자리 실행 digest>'
$revision = '<현재 100% 트래픽 revision>'

# baseline 측정 완료 후 startup 간격만 변경
.\scripts\gcp\set-startup-experiment.ps1 -Mode startup `
  -ExpectedImage $image -ExpectedRevision $revision -ResultPath .\build\study-startup.json
$revision = (Get-Content .\build\study-startup.json -Raw | ConvertFrom-Json).candidateRevision

# startup 측정 완료 후 원복
.\scripts\gcp\set-startup-experiment.ps1 -Mode baseline `
  -ExpectedImage $image -ExpectedRevision $revision -ResultPath .\build\study-startup-restored.json
$revision = (Get-Content .\build\study-startup-restored.json -Raw | ConvertFrom-Json).candidateRevision

# 원복 확인 및 baseline 재확인 측정 후 AOT만 변경
.\scripts\gcp\set-startup-experiment.ps1 -Mode aot `
  -ExpectedImage $image -ExpectedRevision $revision -ResultPath .\build\study-aot.json
$revision = (Get-Content .\build\study-aot.json -Raw | ConvertFrom-Json).candidateRevision

# AOT 측정 완료 후 최종 원복
.\scripts\gcp\set-startup-experiment.ps1 -Mode baseline `
  -ExpectedImage $image -ExpectedRevision $revision -ResultPath .\build\study-final-restored.json
```

스크립트는 기대한 단일 100% revision·이미지·실험 플래그를 검사하고 0% candidate를 생성합니다. 이미지와 다른 runtime 설정이 유지됐는지, candidate Ready 상태를 확인한 뒤 트래픽을 전환합니다. 전환 후 실패하면 직전 트래픽을 복구하며, 다른 작업이 끼어들면 그 변경을 덮어쓰지 않습니다. 결과 JSON에는 임의 환경변수·인증정보를 저장하지 않습니다.

실패 시 결과의 `status`, `rollback`과 실제 트래픽·template를 확인하고 원인을 해소합니다. 다른 배포나 미승격 template가 있으면 자동 진행하지 않습니다. 성공한 각 전환 뒤에도 이미지, 플래그, probe, 트래픽을 다시 확인한 뒤 측정을 시작합니다.

## 측정과 해석

1. 같은 새 이미지의 baseline 자연 cold/warm 쌍을 최소 3회 확보합니다.
2. startup 모드로 바꾸고 배포로 생긴 시작을 제외한 뒤 자연 cold/warm 쌍을 최소 3회 확보합니다.
3. baseline으로 원복하고 설정·동작을 확인합니다. 가능하면 baseline 표본도 추가해 시간대별 변동을 확인합니다.
4. AOT 모드로 바꾸고 같은 방식으로 최소 3쌍을 확보합니다. 실제 `SPRING_AOT_RUNTIME enabled=true` 및 `Starting AOT-processed PlimapApplication` 로그를 확인합니다. 환경변수 값만으로 적용됐다고 판단하지 않습니다.
5. baseline으로 원복하고 최종 설정·정상 응답을 확인합니다.

각 표본은 revision, immutable digest, Git SHA, 모드, probe 정책, 시각, 인스턴스 식별값, CPU·메모리, 브라우저 버전·캐시 조건과 측정 도구를 기록합니다. 실험 중 다른 코드·이미지·브라우저·DB·Secret 버전이 바뀌면 같은 조건으로 묶지 않고 별도로 표시합니다.

자연 scale-to-zero 여부는 Cloud Monitoring 인스턴스 지표와 해당 요청의 신규 인스턴스 시작 로그를 함께 확인합니다. 정기 health 요청·warm-up·최소 인스턴스로 인스턴스를 유지하지 않습니다. 강제 재시작이나 배포로 발생한 시작을 자연 cold로 세지 않습니다. 기존 [AppCDS 측정 원칙](APPCDS.md)도 유지합니다.

| 관측값 | 역할 |
| --- | --- |
| `main → APPLICATION_READY` | 애플리케이션 내부 준비 시간. AOT의 직접 대상 |
| READY → startup 검사 성공 시각 | 플랫폼 확인 대기 가설 확인. READY가 검사 성공보다 늦으면 음수도 그대로 기록 |
| Cloud Run 시작 지표·startup 성공 로그 | 컨테이너·플랫폼 관측. main 시간과 더하지 않음 |
| 최초 API 응답·튜토리얼 표시 | 실제 사용자 대기의 최종 결과 |
| warm 응답·오류·검사 횟수 | 부작용과 관측 조건 확인 |

표본별 값, 중앙값, 최솟값~최댓값을 함께 제시합니다. 각 시간은 기준점이 달라 겹칠 수 있으므로 합산하지 않습니다. AOT에서 설정 클래스 분석 단계 로그가 사라지면 0초라고 대입하지 않고, AOT가 그 경로를 대체했는지 확인합니다. 내부 READY 단축과 화면 단축을 별도로 결론 내며 3회는 탐색적 결과로 설명합니다. 효과가 작거나 일관되지 않으면 그 결과도 기록합니다.

## 빌드·기능 검증

Docker는 prod Bean 생성물을 `-PplimapAot=true`로 만들고 생성 initializer가 JAR에 포함됐는지 검사합니다. 이 처리는 가짜 값과 `RUN --network=none`을 사용하며 실제 자격 증명을 전달하지 않습니다. AOT 처리는 컨텍스트를 실제 운영 서비스처럼 실행하는 단계가 아니므로 Flyway·Hibernate validation 설정을 끄지 않습니다. AppCDS archive 학습은 별도의 기존 격리 설정을 유지합니다. 최종 이미지에는 학습용 설정·더미 ADC가 들어가지 않습니다.

```powershell
docker build --platform=linux/amd64 --tag plimap-startup-aot:local .
.\scripts\appcds\test-appcds.ps1 -Image plimap-startup-aot:local
.\scripts\spring-aot\test-spring-aot.ps1 -Image plimap-startup-aot:local
.\scripts\gcp\test-startup-experiment.ps1
.\scripts\gcp\test-deploy-prod-image.ps1
.\gradlew.bat clean build
```

AOT 기능 검증은 internal Docker 네트워크·가짜 자격 증명·일회용 PostGIS/Redis를 사용하며 두 서비스의 TLS를 활성화합니다. prod의 Flyway·스키마 검증, health/readiness, 인증 차단, CSRF, 공개 지도 조회, 잘못된 로그인 요청 검증, 외부 호출 없는 OAuth redirect를 OFF/ON 모두 확인합니다. ON에서는 실제 생성 initializer 로드도 확인합니다. 로컬 READY 시간은 기능 진단용이며 논문용 Cloud Run 측정 결과가 아닙니다. 실제 제공자 OAuth 로그인과 외부 저장소 연동 완료까지 검증한 것은 아닙니다.

AuthController의 기존 `@Valid` 선언과 Docs 인터페이스 선언을 맞춰 AOT의 validation 메타데이터 생성 오류를 수정했습니다. API 입력 조건을 새로 추가하는 변경은 아닙니다. 이 부분은 별도 회귀 테스트로 확인합니다.

## 3초 검사와 최종 조합 후속 비교

1. 기존 동일 이미지에서 `aot` 3쌍과 `aot-quick` 3쌍을 비교합니다. 현재 이미지의 AOT ON·5초 결과를 재사용한다면 이미지·JDK·브라우저·다른 실행 조건의 동일성을 재확인하고 시간대 차이를 기록합니다.
2. 조합 지원 릴리스는 사용자가 main에 배포합니다. 새 이미지에서는 먼저 `baseline` 3쌍을 새로 수집한 다음 `combined` 3쌍을 수집합니다. 앞선 이미지의 baseline으로 최종 조합 효과를 계산하지 않습니다.
3. 각 처리 모드 사이에는 `baseline`으로 원복합니다. `set-startup-experiment.ps1`의 `-Mode aot-quick|combined|combined-baseline`을 사용하고, 기대하는 단일 100% revision과 immutable digest를 `-ExpectedRevision`, `-ExpectedImage`로 전달합니다. 같은 이미지·다른 자원·네트워크·readiness 조건의 유지와 트래픽·실패 시 복구를 확인합니다.
4. 모든 표본은 자연 scale-to-zero 이후 신규 API 기동과 그 인스턴스의 warm을 연결합니다. 배포·검증 요청의 기동은 제외합니다. partial 결과에는 1/3·2/3을 표시하고, 3쌍이 검증된 뒤 중앙값·범위·개선율을 정리합니다.
5. 최종 측정 후 `baseline`으로 원복하고 실제 AppCDS OFF·AOT OFF·5초/24회·시작 계측 ON과 예상 비로그인 응답을 확인합니다. 수집기·브라우저가 종료됐는지도 확인합니다.

조합은 현재와 같은 JAR·JRE에서 학습한 하나의 `/app/application.jsa`를 공유합니다. 학습 범위 자체를 바꾸는 변수를 추가하지 않습니다. AOT 생성 initializer가 archive에 들어 있다고 가정하지 않고, 실제 AOT 초기화 사용과 애플리케이션 클래스의 `shared objects file (top)` 로드를 각각 검증합니다. 기능 검증이 실패하면 조합 측정으로 넘어가지 않습니다.

AppCDS 단독은 기존 `-Xshare:auto`를 유지합니다. AOT+AppCDS 조합은 `-Xshare:on`으로 실행하여 사용할 수 없는 archive를 조용히 무시하지 않습니다. 누락·손상 archive는 시작 실패로 검증합니다. `test-spring-aot.ps1`은 동일 이미지의 AOT OFF·AOT ON·AOT+AppCDS를 격리한 prod TLS PostGIS/Redis에서 검증합니다. 이 로컬 READY 값은 Cloud Run 성능 표본이 아닙니다.

간격 5초→3초의 검사 빈도는 동일 기동 시간당 약 1.67배입니다. DB·Redis health 확인에 추가 부담이 있을 수 있으므로 요청 실패·기동 실패·warm·CPU·메모리를 함께 기록합니다. READY→startup 성공 로그 간격을 순수 검사 비용으로 해석하거나 각 기법의 단축률을 합산하지 않습니다. 효과가 없거나 조합이 더 느려도 표본을 보존하고 예정 비교 후 논문 작성으로 넘어갑니다.

공식 근거: [Spring Boot Gradle AOT](https://docs.spring.io/spring-boot/gradle-plugin/aot.html), [JVM AOT 실행과 제약](https://docs.spring.io/spring-boot/reference/packaging/aot.html), [Java 21 CDS와 추출 실행](https://docs.spring.io/spring-boot/reference/packaging/aot-cache.html#packaging.aot-cache.cds), [Cloud Run 서비스 설정 변경](https://docs.cloud.google.com/sdk/gcloud/reference/run/services/update).
