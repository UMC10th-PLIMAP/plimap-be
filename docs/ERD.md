# PLIMAP ERD

> 위치 기반 음악 공유 서비스 데이터베이스 설계서
>
> v0.12.0 | 2026-11-02

---

## 테이블 목록 (18개)

| 도메인 | 테이블 | 설명 |
|---|---|---|
| **member** | member | 회원 프로필, 닉네임, 이름, 소개, 온보딩 상태, 가입 provider·권한·벌점/정지/탈퇴 사유·신고 카운트 |
|  | member_follow | 회원 간 팔로우 관계 |
|  | social_account | 카카오, 구글, 애플 소셜 계정, 탈퇴 시 연결 끊기용 애플/구글 refresh token(암호화) |
|  | terms | 이용약관 및 개인정보 처리방침 버전 |
|  | member_terms_agreement | 회원별 약관 동의 이력 |
| **place** | place | 장소 검색·주소 검색 또는 지도에서 선택한 장소와 PostGIS 위치 |
|  | place_search_history | 회원별 최근 선택 장소와 위치 스냅샷 |
|  | place_bookmark | 회원이 저장한 장소 북마크 |
| **track** | track | 외부 음악 제공자의 곡 메타데이터 |
|  | place_track | 한 장소에 PIN으로 등록된 곡 묶음과 공개 PIN·하트 수 |
|  | place_track_like | 장소별 곡에 누른 하트 |
| **pin** | pin | 회원이 장소의 곡에 등록한 재생 구간·소개·피드 공개 여부·따봉 수·신고 카운트 |
|  | tag | PIN 소개에 사용하는 고정 태그 카탈로그 |
|  | pin_tag | PIN과 태그의 다대다 연결 |
|  | pin_like | 개별 사용자 PIN에 누른 따봉 |
| **report** | report | 회원 또는 공개 PIN에 대한 회원 신고 이력 |
| **notification** | notification | 팔로우·PIN 등록·PIN 좋아요에 대해 수신자에게 전달되는 알림(내소식) |
| **inquiry** | inquiry | 로그인 여부와 무관하게 접수하는 1:1 문의(카테고리·제목·내용·답변받을 이메일) |

---

## ERD 관계도

```
member
   ├── 1:N ─ member_follow (팔로워, follower_id)
   ├── 1:N ─ member_follow (팔로잉 대상, following_id)
   ├── 1:N ─ social_account (소셜 로그인: 카카오/구글/애플)
   ├── 1:N ─ member_terms_agreement ─── N:1 ─ terms (약관 버전별 동의)
   ├── 1:N ─ place_search_history (최근 선택 장소)
   ├── 1:N ─ place_bookmark (장소 북마크)
   ├── 1:N ─ pin (장소에 곡 PIN 등록)
   ├── 1:N ─ place_track_like (장소별 곡 하트)
   ├── 1:N ─ pin_like (개별 PIN 따봉)
   ├── 1:N ─ report (신고자)
   ├── 1:N ─ report (신고 대상 회원, nullable)
   ├── 1:N ─ notification (수신자, recipient)
   ├── 1:N ─ notification (행위자, actor)
   └── 1:N ─ inquiry (작성자, member_id nullable — 비로그인·탈퇴 회원은 null)

place 
   ├── 1:N ─ place_search_history (저장된 장소 참조, place_id nullable)
   ├── 1:N ─ place_bookmark ─── N:1 ─ member (장소 북마크)
   └── 1:N ─ place_track ─┬─ N:1 ─ track (장소에 등록된 곡)
                          ├─ 1:N ─ pin ─┬─ N:1 ─ member (등록자)
                          │             ├─ 1:N ─ pin_tag ─── N:1 ─ tag (최대 4개)
                          │             ├─ 1:N ─ pin_like ─── N:1 ─ member (따봉)
                          │             └─ 1:N ─ notification (PIN 등록·좋아요 알림, nullable)
                          └─ 1:N ─ place_track_like ─── N:1 ─ member (하트)

member ─── N:N ─ member (via member_follow, 자기참조 팔로우)
member ─── N:N ─ terms (via member_terms_agreement)
member ─── N:N ─ place (via place_bookmark)
member ─── N:N ─ place_track (via place_track_like)
member ─── N:N ─ pin (via pin_like, 따봉)
pin ─── N:N ─ tag (via pin_tag)
pin ─── N:1 ─ place (JPA 직접 참조, DB는 place_track 복합 FK로 동일 장소 보장)
pin ─── 1:N ─ report (신고 대상 PIN, nullable)
pin ─── 1:N ─ notification (PIN 등록·좋아요 알림, nullable)
```

---

## ENUM 정의

~~~java
MemberStatus: ACTIVE, SUSPENDED, WITHDRAWN
MemberRole: USER, ADMIN
WithdrawalReason: VOLUNTARY, PENALTY
AuthProvider: KAKAO, GOOGLE, APPLE
TermsType: SERVICE, PRIVACY, LOCATION, MARKETING
PlaceSource: PLACE_SEARCH, ADDRESS_SEARCH, MAP_SELECTION
PinSortType: POPULAR, LATEST
PlaceTrackSort: POPULAR, LATEST
AvailabilityStatus: CREATABLE_NEW_PLACE, 
                       OUT_OF_RANGE, TOO_CLOSE_TO_PIN
NicknameCheckFailReason: TOO_SHORT, TOO_LONG, INVALID_FORMAT,
                         FORBIDDEN_WORD, DUPLICATE
ReportCategory: PERSONAL_INFORMATION_EXPOSURE, OBSCENE_OR_HARMFUL,
                ABUSE_OR_HATE_SPEECH, COMMERCIAL_OR_PROMOTIONAL,
                OTHER
NotificationType: FOLLOW, PIN_CREATED, PIN_LIKED
InquiryCategory: ACCOUNT_SUSPENSION_OR_WITHDRAWAL, LOGIN_AUTH_ERROR,
                 PIN_REGISTRATION_OR_PLAYBACK_ERROR, REPORT_SANCTION_APPEAL,
                 APP_BUG_OR_ERROR, OTHER
~~~

| Enum | 표현하는 상태 | 저장 방식 |
|---|---|---|
| `MemberStatus` | 회원의 정상 이용·정지·탈퇴 상태 | `member.status`에 저장 |
| `PlaceSource` | 장소가 외부 장소 검색·주소 검색·지도 직접 선택 중 어느 경로로 만들어졌는지 구분 | `place.source`에 저장 |
| `PinSortType` | 장소별 곡에 등록된 PIN 목록을 인기순·최신순 중 어떤 기준으로 조회할지 표현 | API 요청용, DB에 저장하지 않음 |
| `PlaceTrackSort` | 장소의 곡 목록을 인기순·최신순 중 어떤 기준으로 조회할지 표현 | API 요청용, DB에 저장하지 않음 |
| `AvailabilityStatus` | 현재 위치와 장소·기존 PIN 상태를 바탕으로 PIN 등록 가능 여부와 불가 사유를 표현 | API 응답용 계산값, DB에 저장하지 않음 |
| `NicknameCheckFailReason` | 닉네임 사용 불가 사유를 길이·형식·금칙어·중복 기준으로 표현 | API 응답용 계산값, DB에 저장하지 않음 |
| `MemberRole` | 회원이 일반 사용자인지 관리자인지 구분 | `member.role`에 저장 |
| `WithdrawalReason` | 탈퇴가 자발적 탈퇴인지 벌점 4점 누적에 의한 자동 탈퇴인지 구분(재가입 허용 여부를 가름) | `member.withdrawal_reason`에 저장 |
| `AuthProvider` | 회원이 인증한 소셜 로그인 제공자 | `social_account.provider`에 저장, 최초 가입 provider는 `member.join_provider`에 별도 보존 |
| `TermsType` | 약관이 서비스 이용약관인지 개인정보 처리방침인지 구분 | `terms.type`에 저장 |
| `PinRegistrationStatus` | 현재 위치와 장소·기존 PIN 상태를 바탕으로 PIN 등록 가능 여부와 불가 사유를 표현 | API 응답용 계산값, DB에 저장하지 않음 |
| `ReportCategory` | 개인정보 노출·음란/유해·욕설/혐오 표현·상업적/홍보성·기타 신고 사유 | `report.category`에 저장 |
| `NotificationType` | 알림이 팔로우·PIN 등록·PIN 좋아요 중 어떤 이벤트로 생성됐는지 구분 | `notification.type`에 저장 |
| `InquiryCategory` | 계정 정지/탈퇴·로그인/인증 오류·PIN 등록/재생 오류·신고/제재 이의제기·앱 버그/오류·기타 문의 사유 | `inquiry.category`에 저장 |

음악 제공자는 화면에서 특정 서비스가 확정되지 않았으므로 track.provider에 문자열로 저장하고,
연동 서비스가 확정될 때 CHECK 제약 또는 Enum으로 좁힌다.

PIN의 피드 공개 여부는 두 가지 값만 필요하므로 Enum 대신 `pin.is_feed_public` Boolean 컬럼에 저장한다.

알림은 `type`에 따라 대상 PIN 존재 여부가 갈린다. `FOLLOW`는 `pin_id`가 `NULL`이어야 하고, `PIN_CREATED`·`PIN_LIKED`는 `pin_id`가 필수다(`chk_notification_pin_required`).

---

## PostgreSQL DDL

~~~sql
-- ============================================
-- PLIMAP Database Schema (PostgreSQL)
-- ============================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================
-- 1. MEMBER
-- ============================================

CREATE TABLE member
(
    id                       BIGINT GENERATED BY DEFAULT AS IDENTITY,
    nickname                 VARCHAR(30),
    withdrawn_nickname       VARCHAR(10),
    name                     VARCHAR(7),
    introduction             VARCHAR(100),
    profile_image_object_key VARCHAR(500),
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    onboarding_completed_at  TIMESTAMPTZ,
    join_provider            VARCHAR(20),
    penalty_point            INTEGER     NOT NULL DEFAULT 0,
    suspended_until          TIMESTAMPTZ,
    withdrawal_reason        VARCHAR(20),
    role                     VARCHAR(20) NOT NULL DEFAULT 'USER',
    report_count             INTEGER     NOT NULL DEFAULT 0,
    last_penalty_category    TEXT,
    last_penalty_detail      TEXT,
    last_penalty_period      TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at               TIMESTAMPTZ,
    CONSTRAINT pk_member PRIMARY KEY (id),
    CONSTRAINT chk_member_nickname_length
        CHECK (nickname IS NULL OR char_length(nickname) BETWEEN 2 AND 30),
    CONSTRAINT chk_member_withdrawn_nickname_length
        CHECK (withdrawn_nickname IS NULL OR char_length(withdrawn_nickname) BETWEEN 2 AND 10),
    CONSTRAINT chk_member_nickname_format
        CHECK (nickname IS NULL OR nickname ~ '^[가-힣A-Za-z0-9]+$'),
    CONSTRAINT chk_member_name_length
        CHECK (name IS NULL OR char_length(name) BETWEEN 2 AND 7),
    CONSTRAINT chk_member_name_format
        CHECK (name IS NULL OR name ~ '^[가-힣A-Za-z0-9]+$'),
    CONSTRAINT chk_member_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT chk_member_onboarding
        CHECK (onboarding_completed_at IS NULL OR nickname IS NOT NULL),
    CONSTRAINT chk_member_join_provider
        CHECK (join_provider IS NULL OR join_provider IN ('KAKAO', 'GOOGLE', 'APPLE')),
    CONSTRAINT chk_member_penalty_point
        CHECK (penalty_point >= 0),
    CONSTRAINT chk_member_withdrawal_reason
        CHECK (withdrawal_reason IS NULL OR withdrawal_reason IN ('VOLUNTARY', 'PENALTY')),
    CONSTRAINT chk_member_withdrawal_reason_status
        CHECK (withdrawal_reason IS NULL OR status = 'WITHDRAWN'),
    CONSTRAINT chk_member_suspended_until_status
        CHECK (suspended_until IS NULL OR status = 'SUSPENDED'),
    CONSTRAINT chk_member_role
        CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_member_report_count
        CHECK (report_count >= 0),
    CONSTRAINT chk_member_last_penalty_category
        CHECK (
            last_penalty_category IS NULL OR last_penalty_category IN (
                'PERSONAL_INFORMATION_EXPOSURE',
                'OBSCENE_OR_HARMFUL',
                'ABUSE_OR_HATE_SPEECH',
                'COMMERCIAL_OR_PROMOTIONAL',
                'OTHER'
            )
        ),
    CONSTRAINT chk_member_last_penalty_detail
        CHECK (
            (last_penalty_category = 'OTHER' AND last_penalty_detail IS NOT NULL AND last_penalty_detail ~ '[^[:space:]]')
            OR (last_penalty_category IS DISTINCT FROM 'OTHER' AND last_penalty_detail IS NULL)
        ),
    CONSTRAINT chk_member_last_penalty_period
        CHECK (
            last_penalty_period IS NULL OR last_penalty_period IN (
                'ONE_DAY',
                'THREE_DAYS',
                'FIVE_DAYS',
                'PERMANENT'
            )
        )
);

CREATE UNIQUE INDEX uk_member_nickname_ci
    ON member (lower(nickname))
    WHERE nickname IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE member_follow
(
    follower_id  BIGINT      NOT NULL,
    following_id BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_member_follow PRIMARY KEY (follower_id, following_id),
    CONSTRAINT fk_member_follow_follower
        FOREIGN KEY (follower_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_follow_following
        FOREIGN KEY (following_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT chk_member_follow_self
        CHECK (follower_id <> following_id)
);

CREATE INDEX idx_member_follow_following_id ON member_follow (following_id);

CREATE TABLE social_account
(
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY,
    member_id        BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email            VARCHAR(320),
    encrypted_refresh_token TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_social_account PRIMARY KEY (id),
    CONSTRAINT uk_social_account_provider_subject
        UNIQUE (provider, provider_subject),
    CONSTRAINT uk_social_account_member_provider
        UNIQUE (member_id, provider),
    CONSTRAINT fk_social_account_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT chk_social_account_provider
        CHECK (provider IN ('KAKAO', 'GOOGLE', 'APPLE'))
);

CREATE INDEX idx_social_account_member
    ON social_account (member_id);

CREATE TABLE terms
(
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY,
    type         VARCHAR(20)  NOT NULL,
    version      VARCHAR(20)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    is_required  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    effective_at TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_terms PRIMARY KEY (id),
    CONSTRAINT uk_terms_type_version UNIQUE (type, version),
    CONSTRAINT chk_terms_type CHECK (type IN ('SERVICE', 'PRIVACY', 'LOCATION', 'MARKETING'))
);

CREATE INDEX idx_terms_active
    ON terms (type, is_active, effective_at DESC);

CREATE TABLE member_terms_agreement
(
    member_id    BIGINT      NOT NULL,
    terms_id     BIGINT      NOT NULL,
    agreed       BOOLEAN     NOT NULL,
    agreed_at    TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_member_terms_agreement PRIMARY KEY (member_id, terms_id),
    CONSTRAINT fk_member_terms_agreement_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_terms_agreement_terms
        FOREIGN KEY (terms_id) REFERENCES terms (id) ON DELETE RESTRICT,
    CONSTRAINT chk_member_terms_agreed_at
        CHECK (agreed = FALSE OR agreed_at IS NOT NULL)
);

CREATE INDEX idx_member_terms_agreement_terms
    ON member_terms_agreement (terms_id);

-- ============================================
-- 2. PLACE
-- ============================================

CREATE TABLE place
(
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY,
    name              VARCHAR(100) NOT NULL,
    category          VARCHAR(100),
    address           VARCHAR(255) NOT NULL,
    road_address      VARCHAR(255),
    administrative_region_code VARCHAR(20),
    sido              VARCHAR(100),
    sigungu           VARCHAR(100),
    eup_myeon_dong    VARCHAR(100),
    place_provider    VARCHAR(30),
    provider_place_id VARCHAR(255),
    normalized_address VARCHAR(255),
    source            VARCHAR(20)  NOT NULL,
    location          GEOGRAPHY(POINT, 4326) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT pk_place PRIMARY KEY (id),
    CONSTRAINT chk_place_source
        CHECK (source IN ('PLACE_SEARCH', 'ADDRESS_SEARCH', 'MAP_SELECTION')),
    CONSTRAINT chk_place_location_not_empty
        CHECK (NOT ST_IsEmpty(location::geometry)),
    CONSTRAINT chk_place_provider
        CHECK (
            (
                source = 'PLACE_SEARCH'
                AND place_provider IS NOT NULL
                AND provider_place_id IS NOT NULL
            )
            OR (
                source = 'ADDRESS_SEARCH'
                AND place_provider = 'KAKAO'
                AND provider_place_id IS NULL
            )
            OR (
                source = 'MAP_SELECTION'
                AND place_provider IS NULL
                AND provider_place_id IS NULL
            )
        ),
    CONSTRAINT chk_place_normalized_address
        CHECK (
            (source = 'ADDRESS_SEARCH' AND normalized_address IS NOT NULL)
            OR (source <> 'ADDRESS_SEARCH' AND normalized_address IS NULL)
        ),
    CONSTRAINT chk_place_address_search_category
        CHECK (source <> 'ADDRESS_SEARCH' OR category IS NULL)
);

CREATE UNIQUE INDEX uk_place_provider_id
    ON place (place_provider, provider_place_id)
    WHERE place_provider IS NOT NULL
      AND provider_place_id IS NOT NULL
      AND deleted_at IS NULL;

CREATE INDEX idx_place_location_gist
    ON place USING GIST (location)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_place_name_ci
    ON place (lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_place_active_administrative_region_code
    ON place (administrative_region_code)
    WHERE deleted_at IS NULL
      AND administrative_region_code IS NOT NULL;

CREATE UNIQUE INDEX uk_place_active_address_search_normalized_address
    ON place (normalized_address)
    WHERE source = 'ADDRESS_SEARCH'
      AND deleted_at IS NULL;

CREATE TABLE place_bookmark
(
    place_id   BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_place_bookmark PRIMARY KEY (place_id, member_id),
    CONSTRAINT fk_place_bookmark_place
        FOREIGN KEY (place_id) REFERENCES place (id) ON DELETE CASCADE,
    CONSTRAINT fk_place_bookmark_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
);

CREATE INDEX idx_place_bookmark_member
    ON place_bookmark (member_id, created_at DESC);

CREATE TABLE place_search_history
(
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY,
    member_id         BIGINT       NOT NULL,
    place_id          BIGINT,
    place_provider    VARCHAR(30),
    provider_place_id VARCHAR(255),
    place_name        VARCHAR(100) NOT NULL,
    category          VARCHAR(100),
    address           VARCHAR(255) NOT NULL,
    location          GEOGRAPHY(POINT, 4326) NOT NULL,
    selected_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_place_search_history PRIMARY KEY (id),
    CONSTRAINT fk_place_search_history_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_place_search_history_place
        FOREIGN KEY (place_id) REFERENCES place (id) ON DELETE CASCADE,
    CONSTRAINT chk_place_search_history_target
        CHECK (
            place_id IS NOT NULL
            OR (
                place_provider IS NOT NULL
                AND provider_place_id IS NOT NULL
            )
        ),
    CONSTRAINT chk_place_search_history_provider
        CHECK (
            (place_provider IS NULL AND provider_place_id IS NULL)
            OR (
                place_provider IS NOT NULL
                AND provider_place_id IS NOT NULL
            )
        ),
    CONSTRAINT chk_place_search_history_location_not_empty
        CHECK (NOT ST_IsEmpty(location::geometry))
);

CREATE INDEX idx_place_search_history_member_recent
    ON place_search_history (member_id, selected_at DESC);

CREATE UNIQUE INDEX uk_place_search_history_member_place
    ON place_search_history (member_id, place_id)
    WHERE place_id IS NOT NULL;

CREATE UNIQUE INDEX uk_place_search_history_member_provider
    ON place_search_history (member_id, place_provider, provider_place_id)
    WHERE place_provider IS NOT NULL
      AND provider_place_id IS NOT NULL;

-- ============================================
-- 3. TRACK
-- ============================================

CREATE TABLE track
(
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY,
    provider          VARCHAR(30)   NOT NULL,
    provider_track_id VARCHAR(255)  NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    artist_name       VARCHAR(200)  NOT NULL,
    album_title       VARCHAR(200),
    album_image_url   VARCHAR(1000),
    preview_url       VARCHAR(1000),
    duration_ms       INTEGER,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_track PRIMARY KEY (id),
    CONSTRAINT uk_track_provider_id UNIQUE (provider, provider_track_id),
    CONSTRAINT chk_track_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE INDEX idx_track_title_ci
    ON track (lower(title));

CREATE INDEX idx_track_artist_ci
    ON track (lower(artist_name));

CREATE TABLE place_track
(
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY,
    place_id       BIGINT      NOT NULL,
    track_id       BIGINT      NOT NULL,
    public_pin_count INTEGER   NOT NULL DEFAULT 0,
    like_count     INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT pk_place_track PRIMARY KEY (id),
    CONSTRAINT uk_place_track_id_place UNIQUE (id, place_id),
    CONSTRAINT fk_place_track_place
        FOREIGN KEY (place_id) REFERENCES place (id) ON DELETE CASCADE,
    CONSTRAINT fk_place_track_track
        FOREIGN KEY (track_id) REFERENCES track (id) ON DELETE RESTRICT,
    CONSTRAINT chk_place_track_public_pin_count
        CHECK (public_pin_count >= 0),
    CONSTRAINT chk_place_track_like_count
        CHECK (like_count >= 0)
);

CREATE UNIQUE INDEX uk_place_track_active
    ON place_track (place_id, track_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_place_track_track
    ON place_track (track_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_place_track_place_popular
    ON place_track (place_id, like_count DESC, id DESC)
    WHERE deleted_at IS NULL AND public_pin_count > 0;

CREATE INDEX idx_place_track_place_latest
    ON place_track (place_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND public_pin_count > 0;

CREATE TABLE place_track_like
(
    place_track_id BIGINT      NOT NULL,
    member_id      BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_place_track_like PRIMARY KEY (place_track_id, member_id),
    CONSTRAINT fk_place_track_like_place_track
        FOREIGN KEY (place_track_id) REFERENCES place_track (id) ON DELETE CASCADE,
    CONSTRAINT fk_place_track_like_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
);

CREATE INDEX idx_place_track_like_member
    ON place_track_like (member_id, created_at DESC);

-- ============================================
-- 4. PIN
-- ============================================

CREATE TABLE pin
(
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY,
    member_id      BIGINT       NOT NULL,
    place_id       BIGINT       NOT NULL,
    place_track_id BIGINT       NOT NULL,
    clip_start_ms  INTEGER      NOT NULL,
    introduction   VARCHAR(100) NOT NULL DEFAULT '',
    is_feed_public BOOLEAN      NOT NULL DEFAULT TRUE,
    like_count     INTEGER      NOT NULL DEFAULT 0,
    report_count   INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT pk_pin PRIMARY KEY (id),
    CONSTRAINT fk_pin_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pin_place_track
        FOREIGN KEY (place_track_id, place_id)
        REFERENCES place_track (id, place_id) ON DELETE CASCADE,
    CONSTRAINT chk_pin_clip_start
        CHECK (clip_start_ms >= 0),
    CONSTRAINT chk_pin_introduction
        CHECK (char_length(introduction) <= 100),
    CONSTRAINT chk_pin_like_count
        CHECK (like_count >= 0),
    CONSTRAINT chk_pin_report_count
        CHECK (report_count >= 0)
);

CREATE UNIQUE INDEX uk_pin_member_place_active
    ON pin (member_id, place_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_pin_place_created
    ON pin (place_id, created_at DESC)
    WHERE deleted_at IS NULL AND is_feed_public = TRUE;

CREATE INDEX idx_pin_place_track_popular
    ON pin (place_track_id, like_count DESC, id DESC)
    WHERE deleted_at IS NULL AND is_feed_public = TRUE;

CREATE INDEX idx_pin_place_track_latest
    ON pin (place_track_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND is_feed_public = TRUE;

-- ============================================
-- 4-1. PIN TAG
-- ============================================

CREATE TABLE tag
(
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY,
    name          VARCHAR(20) NOT NULL,
    display_order SMALLINT    NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tag PRIMARY KEY (id),
    CONSTRAINT uk_tag_display_order UNIQUE (display_order),
    CONSTRAINT chk_tag_name_not_blank
        CHECK (char_length(name) BETWEEN 1 AND 20),
    CONSTRAINT chk_tag_name_trimmed
        CHECK (name = trim(name)),
    CONSTRAINT chk_tag_name_without_hash
        CHECK (position('#' IN name) = 0),
    CONSTRAINT chk_tag_display_order
        CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uk_tag_name_ci
    ON tag (lower(name));

INSERT INTO tag (name, display_order)
VALUES ('감성', 0),
       ('고독', 1),
       ('낭만', 2),
       ('몽환', 3),
       ('설렘', 4),
       ('신남', 5),
       ('위로', 6),
       ('잔잔', 7),
       ('청량', 8),
       ('힙함', 9);

CREATE TABLE pin_tag
(
    pin_id        BIGINT      NOT NULL,
    tag_id        BIGINT      NOT NULL,
    display_order SMALLINT    NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_pin_tag PRIMARY KEY (pin_id, tag_id),
    CONSTRAINT uk_pin_tag_order UNIQUE (pin_id, display_order),
    CONSTRAINT fk_pin_tag_pin
        FOREIGN KEY (pin_id) REFERENCES pin (id) ON DELETE CASCADE,
    CONSTRAINT fk_pin_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pin_tag_order
        CHECK (display_order BETWEEN 0 AND 3)
);

CREATE INDEX idx_pin_tag_tag
    ON pin_tag (tag_id);

-- ============================================
-- 4-2. PIN INTERACTION
-- ============================================

CREATE TABLE pin_like
(
    pin_id     BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_pin_like PRIMARY KEY (pin_id, member_id),
    CONSTRAINT fk_pin_like_pin
        FOREIGN KEY (pin_id) REFERENCES pin (id) ON DELETE CASCADE,
    CONSTRAINT fk_pin_like_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
);

CREATE INDEX idx_pin_like_member
    ON pin_like (member_id, created_at DESC);

-- ============================================
-- 5. REPORT
-- ============================================

CREATE TABLE report
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY,
    reporter_member_id BIGINT      NOT NULL,
    reported_member_id BIGINT,
    reported_pin_id    BIGINT,
    category           TEXT        NOT NULL,
    detail             TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_report PRIMARY KEY (id),
    CONSTRAINT fk_report_reporter_member
        FOREIGN KEY (reporter_member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_reported_member
        FOREIGN KEY (reported_member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_reported_pin
        FOREIGN KEY (reported_pin_id) REFERENCES pin (id) ON DELETE RESTRICT,
    CONSTRAINT chk_report_target
        CHECK (
            (reported_member_id IS NOT NULL AND reported_pin_id IS NULL)
            OR (reported_member_id IS NULL AND reported_pin_id IS NOT NULL)
        ),
    CONSTRAINT chk_report_member_self
        CHECK (reported_member_id IS NULL OR reporter_member_id <> reported_member_id),
    CONSTRAINT chk_report_category
        CHECK (
            category IN (
                'PERSONAL_INFORMATION_EXPOSURE',
                'OBSCENE_OR_HARMFUL',
                'ABUSE_OR_HATE_SPEECH',
                'COMMERCIAL_OR_PROMOTIONAL',
                'OTHER'
            )
        ),
    CONSTRAINT chk_report_detail
        CHECK (
            (category = 'OTHER' AND detail IS NOT NULL AND char_length(btrim(detail)) >= 1)
            OR (category <> 'OTHER' AND detail IS NULL)
        )
);

CREATE UNIQUE INDEX uk_report_reporter_member
    ON report (reporter_member_id, reported_member_id)
    WHERE reported_member_id IS NOT NULL;

CREATE UNIQUE INDEX uk_report_reporter_pin
    ON report (reporter_member_id, reported_pin_id)
    WHERE reported_pin_id IS NOT NULL;

-- ============================================
-- 6. NOTIFICATION
-- ============================================

CREATE TABLE notification
(
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY,
    recipient_id BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    pin_id       BIGINT,
    type         TEXT        NOT NULL,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_actor
        FOREIGN KEY (actor_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_pin
        FOREIGN KEY (pin_id) REFERENCES pin (id) ON DELETE RESTRICT,
    CONSTRAINT chk_notification_type
        CHECK (type IN ('FOLLOW', 'PIN_CREATED', 'PIN_LIKED')),
    CONSTRAINT chk_notification_pin_required
        CHECK (
            (type = 'FOLLOW' AND pin_id IS NULL)
            OR (type IN ('PIN_CREATED', 'PIN_LIKED') AND pin_id IS NOT NULL)
        )
);

CREATE INDEX idx_notification_recipient_latest
    ON notification (recipient_id, created_at DESC, id DESC);

-- ============================================
-- 7. INQUIRY
-- ============================================

CREATE TABLE inquiry
(
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY,
    member_id      BIGINT,
    category       TEXT          NOT NULL,
    title          VARCHAR(100)  NOT NULL,
    content        TEXT          NOT NULL,
    contact_email  VARCHAR(320)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inquiry PRIMARY KEY (id),
    CONSTRAINT fk_inquiry_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE SET NULL,
    CONSTRAINT chk_inquiry_category
        CHECK (
            category IN (
                'ACCOUNT_SUSPENSION_OR_WITHDRAWAL',
                'LOGIN_AUTH_ERROR',
                'PIN_REGISTRATION_OR_PLAYBACK_ERROR',
                'REPORT_SANCTION_APPEAL',
                'APP_BUG_OR_ERROR',
                'OTHER'
            )
        ),
    CONSTRAINT chk_inquiry_title
        CHECK (char_length(btrim(title)) >= 1),
    CONSTRAINT chk_inquiry_content
        CHECK (char_length(btrim(content)) >= 1),
    CONSTRAINT chk_inquiry_contact_email
        CHECK (char_length(btrim(contact_email)) >= 1)
);

CREATE INDEX idx_inquiry_member_id ON inquiry (member_id);
~~~

---

## 변경 이력

| 버전    | 날짜         | 변경 내용                                                                                                                 |
|-------|------------|-----------------------------------------------------------------------------------------------------------------------|
| 0.1.0 | 2026-06-28 | Figma 화면 분석을 기반으로 PostgreSQL 14개 테이블을 설계하고 member·place·track·pin 도메인으로 구분, tag를 pin 도메인에 포함                          |
| 0.1.1 | 2026-06-28 | 매핑 테이블을 원본으로 유지하면서 place_track에 PIN·하트·북마크 수, pin에 따봉 수를 조회용 카운트 컬럼으로 추가                                              |
| 0.2.0 | 2026-06-28 | 위치를 PostGIS geography와 GiST 인덱스로 전환하고 화면 정렬 인덱스·고정 태그 카탈로그·API용 PIN Enum을 반영                                          |
| 0.3.0 | 2026-07-02 | 모든 JPA 엔티티에 BaseEntity 공통 시간 컬럼과 소프트 삭제 정책을 적용하고, updated_at 관리를 JPA Auditing으로 통일                                    |
| 0.3.1 | 2026-07-02 | Soft Delete를 member·place·place_track·pin에만 선택 적용하고, AWS RDS에서도 단일 애플리케이션 쓰기 구조인 동안 JPA Auditing을 유지하도록 정책 명확화        |
| 0.4.0 | 2026-07-04 | PIN 피드 공개 여부를 `is_feed_public` Boolean 컬럼으로 추가하고, 공개 PIN 조회 인덱스와 `place_track.public_pin_count` 집계 정책을 반영             |
| 0.4.1 | 2026-07-04 | 장소별 곡 북마크인 `place_track_bookmark`와 `place_track.bookmark_count`를 제거하고, 장소 자체를 저장하는 `place_bookmark`로 변경               |
| 0.5.0 | 2026-07-05 | member 테이블에 `name`(이름)·`introduction`(소개) 컬럼 및 CHECK 제약 추가, 팔로우 관계 관리를 위한 `member_follow` 테이블 추가, 로컬 DB 마이그레이션 섹션 추가  |
| 0.5.1 | 2026-07-12 | `place` 테이블에 nullable `category VARCHAR(100)` 컬럼 추가                                                                   |
| 0.5.2 | 2026-07-15 | 닉네임 최대 길이를 7자에서 10자로 확장(`VARCHAR(10)`, `chk_member_nickname_length`), 이름 최소 길이를 1자에서 2자로 강화(`chk_member_name_length`) |
| 0.6.0 | 2026-07-19 | pin에서 clip_end_ms를 삭제                                                                                                 |
| 0.6.1 | 2026-07-20 | AvailabilityStatus enum 수정                                                                                            |
| 0.7.0 | 2026-07-23 | 회원·공개 PIN 신고 이력을 위한 `report` 테이블과 `ReportCategory`를 추가하고 대상·상세 내용·중복 신고 제약을 반영                         |
| 0.8.0 | 2026-07-31 | 팔로우·PIN 등록·PIN 좋아요 알림(내소식)을 위한 `notification` 테이블과 `NotificationType`을 추가하고, 알림 유형별 대상 PIN 필수 여부 제약을 반영 |
| 0.9.0 | 2026-07-31 | 회원 탈퇴/벌점/정지/관리자 인가를 위해 member에 `join_provider`·`penalty_point`·`suspended_until`·`withdrawal_reason`·`role`·`report_count` 컬럼과 `MemberRole`·`WithdrawalReason` Enum 추가, pin에 신고 자동숨김용 `report_count` 컬럼 추가. 이미 삭제된 `clip_end_ms`를 참조하던 `chk_pin_clip_range` 문서 오류도 함께 제거 |
| 0.9.1 | 2026-07-31 | 자발적 탈퇴 시 마스킹 닉네임("플리맵사용자{memberId}")을 담기 위해 `nickname` 길이를 `VARCHAR(30)`으로 확장하고, 마스킹 전 원래 닉네임을 보존하는 `withdrawn_nickname VARCHAR(10)` 컬럼과 `chk_member_withdrawn_nickname_length` 제약 추가 |
| 0.9.2 | 2026-08-01 | `place`의 행정구역·정규화 주소와 `ADDRESS_SEARCH` 제약·인덱스를 반영하고, 누락된 관계와 API Enum을 보완했으며 삭제된 `clip_end_ms` 제약을 제거 |
| 0.10.0 | 2026-08-07 | 로그인 여부와 무관하게 접수하는 문의를 위한 `inquiry` 테이블과 `InquiryCategory`를 추가. `member_id`는 nullable(비로그인·탈퇴 회원은 null)이며 `ON DELETE SET NULL`로 연결 |
| 0.11.0 | 2026-08-12 | 관리자가 기간·사유를 직접 선택/작성하는 최종 제재 API를 위해 member에 `last_penalty_category`·`last_penalty_detail` 컬럼과 `report`와 동일한 카테고리·detail 정합성 제약 추가 |
| 0.11.1 | 2026-08-13 | 프론트가 "누적 N회차 (N일 정지)" 형태로 제재 상태를 표시할 수 있도록 member에 `last_penalty_period` 컬럼과 `SuspensionPeriod` 값 제약 추가 |
| 0.12.0 | 2026-11-02 | 회원 탈퇴 시 애플/구글 연결을 끊기(revoke) 위해 social_account에 provider refresh token을 AES-GCM으로 암호화해 담는 `encrypted_refresh_token` 컬럼 추가. 카카오는 어드민 키로 연결을 끊으므로 저장하지 않음 |
