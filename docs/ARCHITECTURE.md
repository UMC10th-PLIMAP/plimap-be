# Architecture

## Overview

PLIMAP API는 **도메인 기반 계층형 아키텍처(Package by Feature + Layered Architecture)**를 사용합니다.

각 도메인을 기준으로 패키지를 먼저 나누고, 도메인 내부에서 Controller, Service, Repository, Entity 등의 계층을 분리합니다. 이를 통해 특정 기능을 수정할 때 관련 코드가 하나의 도메인 패키지 안에서 응집되도록 구성합니다.

또한 Service 계층에는 **부분적 CQRS(Command Query Responsibility Segregation)** 방식을 적용합니다.

- `command`: 생성, 수정, 삭제처럼 상태를 변경하는 책임
- `query`: 조회, 목록, 검색처럼 상태를 변경하지 않는 책임

> 참고: 이 구조는 읽기/쓰기 책임을 패키지 수준에서 분리하는 방식이며, 별도의 읽기 DB, 이벤트 소싱, 완전한 CQRS 아키텍처를 의미하지 않습니다.

## Layer Structure

```text
┌─────────────────────────────────────────────────────────────┐
│                      Controller Layer                        │
│                    API 진입점, 요청/응답 처리                    │
├─────────────────────────────────────────────────────────────┤
│                       Service Layer                          │
│              비즈니스 로직 처리, Command/Query 분리              │
├─────────────────────────────────────────────────────────────┤
│                      Repository Layer                        │
│                 데이터 접근, 커스텀 조회 구현 분리                 │
├─────────────────────────────────────────────────────────────┤
│                       Entity Layer                           │
│                         도메인 모델                            │
└─────────────────────────────────────────────────────────────┘
```

### Dependency Direction

계층 간 의존 방향은 아래 흐름을 기본으로 합니다.

```text
Controller → Service → Repository → Entity
```

- Controller는 API 요청을 받고 DTO를 통해 응답을 반환합니다.
- Service는 도메인 비즈니스 로직을 처리합니다.
- Repository는 데이터 접근을 담당합니다.
- Entity는 도메인의 상태와 핵심 규칙을 표현합니다.
- 하위 계층은 상위 계층에 의존하지 않습니다.

## Package Structure

```text
com.example.plimap/
│
├── domain/                         # 도메인 레이어
│   ├── member/                     # 회원·약관
│   ├── auth/                       # 소셜 로그인·JWT
│   ├── home/                       # 홈 화면 복합 조회
│   ├── place/                      # 장소·위치·검색 기록·북마크
│   ├── track/                      # 음악·장소별 곡·좋아요
│   ├── notification/               # 알림·SSE
│   ├── report/                     # 회원·PIN 신고 접수
│   ├── admin/                      # 관리자 전용 API
│   ├── inquiry/                    # 로그인 여부와 무관한 1:1 문의 접수
│   └── pin/                        # PIN 핵심 도메인
│       ├── controller/             # REST API 컨트롤러
│       │   └── docs/               # Swagger 문서용 인터페이스/설명
│       ├── converter/              # Entity, DTO 변환
│       ├── dto/                    # 요청/응답 DTO
│       │   ├── request/
│       │   └── response/
│       ├── entity/                 # JPA 엔티티
│       ├── enums/                  # 도메인 Enum
│       ├── exception/              # 도메인 예외
│       ├── repository/             # 데이터 접근
│       │   └── query/              # 커스텀 조회 인터페이스
│       │       └── impl/           # 커스텀 조회 구현체
│       └── service/                # 비즈니스 로직
│           ├── command/            # 생성, 수정, 삭제
│           │   └── impl/
│           └── query/              # 조회, 목록, 검색
│               └── impl/
│
├── global/                         # 전역 공통 모듈
│   ├── apiPayload/                 # 공통 API 응답, 코드 및 예외 처리
│   │   ├── ApiResponse.java
│   │   ├── code/                   # 성공·에러 코드
│   │   └── exception/              # 비즈니스 예외 및 전역 예외 처리
│   ├── config/                     # 설정 클래스
│   ├── entity/                     # BaseEntity, SoftDeleteEntity 등 공통 엔티티
│   ├── logging/                    # 공통 HTTP 오류 로그
│   ├── security/                   # 인증/인가 보안 설정
│   ├── swagger/                    # Swagger/OpenAPI 설정
│   └── external/                   # 외부 시스템 연동 공통 영역
│
└── PlimapApplication
```

## Domain Structure

### 1. Member

회원, 프로필, 팔로우와 약관 관련 기능을 담당합니다.

### 2. Auth

인증과 토큰 관련 기능을 담당합니다.

### 3. Place

장소, 위치, 검색 기록과 북마크 관련 기능을 담당합니다.

`PlaceQueryService`는 Controller와 PIN·Track·Place Command가 사용하는 공개 조회 계약입니다.
구현체는 이 계약을 유지하면서 장소 검색을 `PlaceSearchQueryService`, 인기 장소 조회를
`PopularPlaceQueryService`에 위임합니다. 상세·북마크·최근 검색 조회는 기존 구현체에 남겨 둡니다.

### 4. Track

음악과 장소별 곡 관련 기능을 담당합니다.

### 5. Pin

PLIMAP의 PIN 핵심 도메인을 담당합니다.

### 6. Report

회원과 공개 PIN의 신고 접수를 담당합니다.

### 7. Inquiry

로그인·비로그인 사용자의 문의 접수와 회원 연결을 담당합니다.

### 8. Notification

회원 활동으로 발생하는 알림의 저장, 조회 및 실시간 전달을 담당합니다.

### 9. Admin

관리자 페이지 관련 API를 담당합니다.

### 10. Home

홈 화면에 필요한 읽기 모델을 조합합니다.

## Domain Event and Notification

`member`와 `pin` 도메인의 Command Service는 알림 도메인에 직접 의존하지 않고 Spring의 애플리케이션 이벤트를 발행합니다.

- `MemberFollowedEvent`: 회원 팔로우 알림
- `PinCreatedEvent`: 팔로워 대상 PIN 등록 알림
- `PinLikedEvent`: PIN 작성자 대상 좋아요 알림

`NotificationEventListener`는 원본 상태 변경이 성공적으로 커밋된 후 `AFTER_COMMIT` 단계에서 이벤트를 처리합니다. PIN 등록 알림은 `notificationTaskExecutor`를 사용해 비동기로 처리합니다.

알림 생성은 `NotificationCommandService`의 새로운 트랜잭션에서 실행되며, 알림 저장 트랜잭션이 커밋된 후 연결된 클라이언트에 SSE 이벤트를 전송합니다.

```text
Member/Pin Command Service
    → Domain Event 발행
    → 원본 트랜잭션 커밋
    → NotificationEventListener
    → NotificationCommandService
    → Notification 저장
    → SSE 실시간 전송
```

## Partial CQRS

PLIMAP은 Service 계층에서 Command와 Query 책임을 분리합니다.

### Command Service

`service/command` 패키지는 상태 변경이 발생하는 유스케이스를 담당합니다.

- 생성
- 수정
- 삭제
- 상태 변경

Command Service는 필요한 경우 트랜잭션을 통해 도메인 상태를 변경합니다.

### Query Service

`service/query` 패키지는 상태 변경 없이 데이터를 조회하는 유스케이스를 담당합니다.

- 단건 조회
- 목록 조회
- 검색
- 필터링

Query Service는 조회 목적에 맞는 DTO를 반환할 수 있으며, 복잡한 조회는 `repository/query` 패키지의 커스텀 조회 로직을 사용합니다.

### Repository Query

`repository/query` 패키지는 기본 JPA Repository만으로 표현하기 어려운 조회를 분리하기 위한 영역입니다.

- `repository`: 기본 CRUD 중심의 데이터 접근
- `repository/query`: 커스텀 조회 인터페이스
- `repository/query/impl`: QueryDSL 등 커스텀 조회 구현체

```text
domain/{domain}/repository/
├── {Entity}Repository.java
└── query/
    ├── {Entity}QueryRepository.java
    └── impl/
        └── {Entity}QueryRepositoryImpl.java
```

기본 CRUD와 단순 조건 조회는 Spring Data JPA Repository가 담당합니다. 동적 조건 조립, 복잡한 검색과 페이징은 Query Repository가 담당합니다. Query Repository는 데이터 접근에만 집중하고 비즈니스 판단과 트랜잭션 흐름은 Query Service에서 처리합니다.

## Database Design

### Base Entity

모든 JPA 엔티티는 생성일과 수정일을 관리하기 위해 `BaseEntity`를 상속하는 것을 기본 원칙으로 합니다. 삭제 이력 보존이 필요한 엔티티만 `SoftDeleteEntity`를 상속합니다.

```java
public class Track extends BaseEntity {
}

public class Pin extends SoftDeleteEntity {
}
```

`createdAt`, `updatedAt`은 `BaseEntity`에서 관리하고, `deletedAt`은 `SoftDeleteEntity`에서 관리합니다. PostgreSQL의 `TIMESTAMPTZ`와 일관된 시점 표현을 위해 Java 타입은 `Instant`를 사용합니다.

### Auditing

엔티티의 생성 시점과 수정 시점은 JPA Auditing을 통해 자동으로 기록합니다.

- `createdAt`: 생성 시점
- `updatedAt`: 마지막 수정 시점

이를 통해 생성일과 수정일을 직접 설정하는 중복 로직을 줄이고, 모든 엔티티의 시간 관리 방식을 통일합니다.

JPA Auditing은 `global.config.JpaAuditingConfig`의 `@EnableJpaAuditing`으로 활성화합니다. `BaseEntity`의 `@CreatedDate`, `@LastModifiedDate`는 이 설정이 등록되어 있을 때 엔티티 저장 이벤트에 맞춰 동작합니다.

데이터베이스 제공자나 실행 환경이 달라져도 시간 기록 방식은 변경하지 않습니다. PLIMAP 백엔드가 유일한 데이터 쓰기 주체인 동안에는 JPA Auditing을 사용합니다. 추후 외부 배치, 관리자 SQL 또는 다른 서비스가 동일한 DB를 직접 수정하게 되면 애플리케이션을 거치지 않는 변경도 기록할 수 있도록 DB Trigger 도입을 재검토합니다.

### Soft Delete

복구 가능성과 이력 보존이 필요한 핵심 엔티티에만 Soft Delete를 적용합니다.

- `deletedAt`: 삭제 처리 시점
- 적용 대상: `Member`, `Place`, `PlaceTrack`, `Pin`

Soft Delete 대상의 삭제 유스케이스는 `repository.delete()` 또는 `repository.deleteById()`를 호출하지 않고 `SoftDeleteEntity.delete()`로 삭제 시점을 기록합니다. 일반 조회는 `deletedAt IS NULL` 조건으로 삭제된 데이터를 제외합니다. 삭제된 데이터가 필요한 관리·복구 기능만 이 조건을 명시적으로 해제합니다.

같은 식별 관계를 다시 생성해야 하는 경우에는 삭제된 row를 중복 삽입하지 않고 조회한 뒤 `SoftDeleteEntity.restore()`로 복구합니다.

약관과 태그는 버전·활성 상태로 관리하고, 약관 동의는 `withdrawnAt`으로 철회 이력을 표현합니다. 검색 기록과 좋아요·북마크·태그 연결 같은 이력·매핑 데이터는 보존 요구사항이 없다면 물리 삭제합니다.

### Entity Design Rule

- Entity는 DB 테이블과 매핑되는 도메인 모델입니다.
- Entity에는 도메인 상태를 변경하는 최소한의 비즈니스 메서드를 둘 수 있습니다.
- Soft Delete 대상의 삭제와 복구는 `SoftDeleteEntity`의 `delete()`, `restore()`를 사용합니다.
- API 요청/응답 DTO를 Entity 내부에 직접 의존시키지 않습니다.
- Entity 생성과 변경은 Service 계층에서 유스케이스 흐름에 맞게 제어합니다.
- Service는 Entity의 생성 시점과 유스케이스 흐름을 제어하며, 실제 인스턴스 생성은 Entity가 제공하는 정적 팩터리 메서드를 사용합니다. 정적 팩터리 메서드는 private 생성자에 선언한 Builder를 통해 Entity를 생성합니다.
