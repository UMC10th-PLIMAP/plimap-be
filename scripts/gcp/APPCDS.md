# AppCDS 적용과 비교 측정 준비

## 목적과 범위

서버가 처음 시작할 때 읽는 애플리케이션·라이브러리 클래스 정보 일부를 미리 정리한 archive로 재사용합니다. 첫 API 응답과 첫 화면 대기를 줄일 수 있는지 검증하기 위한 첫 개선 후보입니다. 설정 추가만으로 개선 효과가 입증되는 것은 아닙니다.

이번 변경은 이미지 생성, 실제 archive 사용 검증과 동일 조건 비교 준비입니다. Java 21을 유지하며 Spring AOT, Native Image, lazy initialization, 프로브·CPU·메모리·네트워크·공개 API·DB 스키마는 변경하지 않습니다. 운영 배포와 실제 자연 cold/warm 측정은 별도로 진행합니다.

## 이미지 구성과 학습 격리

`Dockerfile`의 builder는 기존처럼 `bootJar`를 생성합니다. JRE 학습 단계에서는 Spring Boot tools로 라이브러리를 추출하고 `/app/app.jar`를 실행해 `/app/application.jsa`를 만듭니다. 학습과 최종 실행은 같은 고정 Temurin Java 21 JRE 이미지, 같은 `/app` 경로와 classpath를 사용합니다. JDK builder와 JRE runtime의 digest는 Dockerfile에 고정되어 있습니다. 갱신 시 archive도 다시 만들고 검증합니다.

- 학습 실행은 `prod` 구성을 사용하되 **학습 단계에만** `scripts/appcds/training.properties`를 추가합니다.
- 학습은 `RUN --network=none`에서 수행합니다. DB·Redis·외부 API·GCP 메타데이터에 접근할 수 없습니다.
- 가짜 값과 `.invalid` 호스트만 사용하며 실제 `.env`, Secret Manager, ADC 또는 서비스 계정 키를 전달하지 않습니다. `training-credentials.json`은 실제 인증에 사용할 수 없는 더미입니다.
- 학습에서만 Flyway와 Hibernate 스키마 검증·JDBC 메타데이터 접속을 생략하고 PostgreSQL dialect를 명시합니다. Spring의 `spring.context.exit=onRefresh`로 컨텍스트 초기화 시 종료합니다.
- 같은 경로·JRE에서 `-Xshare:on`으로 다시 초기화하고, 실제 `PlimapApplication`이 `shared objects file (top)`에서 로드되는지 빌드 게이트로 검사합니다. archive 파일이 생기기만 한 상태는 통과하지 않습니다.
- 최종 이미지에는 `/app`의 앱·라이브러리·archive·SHA-256·Java 버전과 entrypoint만 복사합니다. 학습 설정, 더미 ADC, 원본 fat JAR와 상세 검증 로그는 복사하지 않습니다.
- runtime은 기존 non-root UID 10001로 실행하며 shell은 `exec java`로 신호를 전달합니다.

학습에서 생략한 Flyway·DB 접속 경로와 런타임 생성 proxy 등은 archive에 없을 수 있습니다. 일반 런타임은 기존 Flyway·스키마 검증·외부 인증을 정상 수행합니다. archive는 살아 있는 Spring 객체나 DB 연결을 복원하지 않습니다.

## 빌드와 로컬 검증

저장소 루트에서 실행합니다. 운영 자격 증명 파일을 전달하지 않습니다.

```powershell
docker build --platform=linux/amd64 --tag plimap-appcds:local .
./scripts/appcds/test-appcds.ps1 -Image plimap-appcds:local
```

`test-appcds.ps1`은 다음을 검증합니다.

1. non-root 실행, archive checksum, 학습 파일의 최종 이미지 제외.
2. 잘못된 모드와 없는 archive를 애플리케이션 시작 전에 거부.
3. 임시 PostGIS 17/Redis를 외부 통신 불가 내부 네트워크에 생성. 호스트 port와 Docker socket을 컨테이너에 노출하지 않음.
4. 같은 이미지의 AppCDS OFF/ON을 새 JVM으로 각각 실행. 런타임 검증은 가짜 값을 사용하는 `test` profile이며 Flyway와 실제 DB 스키마 검증을 수행.
5. health/readiness·전체 health 200, 비로그인 회원 확인 401, CSRF 200, 실제 DB를 읽는 공개 지도 200.
6. 시작 계측 READY 및 ON의 애플리케이션 클래스 `shared objects file (top)` 로딩, OFF에서는 해당 로딩 0건.
7. 생성한 컨테이너·내부 네트워크를 정리하고 JSON·애플리케이션 로그·class-load 로그를 새 결과 폴더에 보존. 기존 결과 폴더는 덮어쓰지 않음.

기본 결과 폴더는 `build/appcds-validation-<임의값>`이며 `-ResultDirectory`로 다른 새 경로를 선택할 수 있습니다. `clean`은 `build` 결과를 지우므로 보존할 증거는 별도 연구 폴더로 복사합니다. 로그에는 클래스 이름과 가짜 검증 설정만 있고 응답 본문·쿠키·토큰은 저장하지 않습니다.

로컬 READY 시간은 공유 개발 PC, 기능 검증용 설정과 클래스 로딩 진단 로그의 영향을 받습니다. 운영 개선 결과로 인용하지 않습니다. 외부 OAuth/GCS의 실제 쓰기 동작은 이 검증에 포함하지 않습니다. CI의 `AppCDS image and application archive` job도 같은 검증을 수행합니다.

## 실행 모드와 실패 판별

| 설정 | 동작 |
|---|---|
| `PLIMAP_APP_CDS_ENABLED=true` (기본) | 추출 구조에서 애플리케이션 archive 사용 요청 |
| `PLIMAP_APP_CDS_ENABLED=false` | 같은 추출 구조에서 애플리케이션 archive 없이 실행. JDK 기본 CDS는 유지 |
| 기타 값 | 시작 전에 exit 64 |
| ON인데 archive가 없음 | 시작 전에 실패 |

모드와 무관하게 `-Xlog:cds=info`를 기록합니다. ON은 `-Xshare:auto`를 사용하므로 JDK·classpath·JVM 옵션 등이 맞지 않으면 앱이 일반 로딩으로 시작할 수 있습니다. 따라서 `APP_CDS_CONFIGURATION enabled=true`만으로 성공이라고 세지 않습니다. 해당 인스턴스의 JVM 로그에서 `/app/application.jsa`의 open과 dynamic archive mapping 성공, 실패·mismatch·disabled 메시지 여부를 확인해야 합니다. 상세한 애플리케이션 클래스 source 검증은 위의 로컬/CI 진단으로 수행합니다.

`-Xshare:on` 강제 검증은 빌드 단계에만 사용합니다. 운영은 archive 불일치로 JVM을 무조건 종료시키지 않지만, fallback 표본은 정상 AppCDS 효과 표본에서 분리합니다. 대체 설정을 조용히 성공으로 처리하지 않습니다.

기본 entrypoint는 전체 class-load 로그를 켜지 않습니다. 운영 본 측정에서 상세 진단을 추가하면 시작 오버헤드가 달라질 수 있으므로 모든 비교군에서 같은 설정을 사용하거나 별도 진단 기동으로 분리합니다. 임의의 `JAVA_TOOL_OPTIONS`로 classpath·archive를 바꾸거나 추가 agent를 주입한 실행은 기본 실험 조건과 구분합니다.

## Prod 배포 준비와 설정 유지

기존 `main` CI 성공 → production Environment 승인 → 후보 revision 검증 → 트래픽 전환 절차를 유지합니다. PR 생성이나 브랜치 push만으로 Prod를 배포하지 않습니다.

Prod 워크플로에서 다음 GitHub Variables를 읽습니다.

| 변수 | 미설정 시 기본값 | 역할 |
|---|---|---|
| `PROD_APP_CDS_ENABLED` | `true` | 이미지에 포함된 애플리케이션 archive 선택 |
| `PROD_STARTUP_MONITORING_ENABLED` | `true` | 기존 started/READY·단계 요약 계측 유지 |

두 변수는 `true`/`false`만 허용됩니다. 수동 `deploy-prod.ps1`은 `-AppCdsEnabled true`가 기본이며, 시작 계측은 기존 동작을 유지해 `false`가 기본입니다. 수동 실험 배포에서는 `-StartupMonitoringEnabled true`를 명시합니다. 두 값은 env-vars-file과 배포 결과 JSON·Actions summary에 남습니다. 배포의 env-vars-file 교체로 이전 수동 계측 플래그가 사라지는 문제를 방지합니다.

Dev도 기본 Docker 이미지를 빌드하므로 AppCDS를 사용합니다. Dev 시작 계측 기본값은 기존 애플리케이션 설정인 false를 유지합니다.

배포 전에는 로컬/CI 성공과 실제 archive 로딩을 확인하고 배포 후에는 새 revision의 실제 환경변수·image digest·Java 버전·archive mapping을 확인합니다. GitHub Variable 변경만으로 실행 중인 revision이 바뀌지는 않습니다. 새 배포가 필요합니다.

## 비교 조건: 패키징 효과와 AppCDS 효과 분리

| 조건 | 목적 |
|---|---|
| 기존 fat JAR 기준선 | 기존 사용자 대기와의 전체 비교 |
| 같은 신규 이미지 + AppCDS OFF | 추출 실행 구조의 대조군 |
| 같은 신규 이미지 + AppCDS ON | OFF 대비 애플리케이션 archive의 효과 |

기존 fat JAR 대조 이미지가 더 필요하면 같은 commit과 고정 JRE에서 `docker build --target baseline --tag plimap-fatjar:local .`로 만들 수 있습니다. 기본 빌드는 마지막 `runtime` stage입니다. baseline stage는 추가 JVM 로그와 AppCDS entrypoint가 없으므로 이 차이를 기록합니다.

순수 AppCDS 효과는 **동일 신규 이미지의 OFF 대 ON**으로 비교합니다. 기존 기준선과 신규 ON을 바로 비교한 결과에는 패키징 효과 등이 함께 포함될 수 있습니다. 기존 표본을 삭제하거나 새 표본으로 덮어쓰지 않습니다. DB 데이터나 외부 의존성 상태 차이도 보존합니다.

## 배포 이후 실제 측정 체크리스트

- 조건별로 단일 100% revision인지, 요청과 신규 instance·READY가 연결되는지 확인.
- 가능하면 최신 active/idle 0을 직접 확인하고 자연 요청 기동을 관측. 과거0 근거와 최신0 직접 확인을 구분. 결측을 0으로 치환하지 않음.
- 기존과 같은 기본 URL, 비로그인, 새 Chrome·빈 저장소/캐시, 같은 viewport·Service Worker 차단과 튜토리얼 표시 종료점 사용.
- 배포 검증 기동은 자연 요청 기동 표본과 구분. 강제 재시작·주기 워밍을 추가하지 않음.
- `PLIMAP_STARTUP_MONITORING_ENABLED=true`, buffer 2048/max-steps 10, JVM 옵션·자원·CPU Boost·프로브·네트워크 조건을 비교군 사이에서 유지.
- 같은 이미지 OFF/ON이어도 환경변수 변경으로 revision은 달라짐. 각 조건의 image/source commit·Java/기반 image digest·모드·revision·instance와 web 서비스 시작 상태 기록.
- 각 cold 이후 같은 instance의 warm 비교. 초기 비교는 조건별 독립 3쌍을 출발점으로 검토하되 통계적 충분성으로 해석하지 않고 필요시 반복 추가.
- 주 지표: 튜토리얼 표시. 원인 지표: 최초 API·pending·main→READY·플랫폼 시작. 부작용: warm·최초 DB 기능·오류·메모리. 정의가 중첩된 시간을 합산하지 않음.
- archive fallback·실패·조건 변경·제외 사유도 보존. 상세 class-load 진단이 켜진 로컬 값을 본 측정에 합산하지 않음.
- Spring 준비만 빨라지고 화면이 그대로면 프로브·트래픽 대기 등 다음 구간을 분석. 다른 개선 기법을 동시에 넣지 않음.

## 복구

같은 이미지에서 애플리케이션 archive만 끄려면 승인된 다음 배포의 `PROD_APP_CDS_ENABLED=false`를 사용합니다. 시작 계측은 비교를 위해 true를 유지할 수 있습니다. 이는 추출 구조 대조군이며 기존 fat JAR 구조로 돌아가는 것은 아닙니다. 즉시 운영 복구가 필요하면 기존 승인 절차로 이전 정상 revision을 복구합니다. 프로브·인증·스키마 검증을 약하게 만드는 복구는 하지 않습니다.

## 공식 근거

- [Spring Boot: Java 21 CDS 및 추출 실행](https://docs.spring.io/spring-boot/reference/packaging/aot-cache.html#packaging.aot-cache.cds)
- [Spring Boot: 실행 JAR 추출](https://docs.spring.io/spring-boot/reference/packaging/efficient.html)
- [Java 21: CDS·기본 archive·동적 archive·Xshare 동작](https://docs.oracle.com/en/java/javase/21/vm/class-data-sharing.html)
