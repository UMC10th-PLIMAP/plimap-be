-- 공용 데모 회원 1명만 준비한다. 기존 회원이나 스키마는 변경하지 않는다.
-- psql -v ON_ERROR_STOP=1 -f scripts/gcp/prepare-demo-account.sql
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15s';
SELECT pg_advisory_xact_lock(hashtext('plimap:demo-account:333'));

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM member WHERE nickname = '테스트계정' OR name = '테스트계정') THEN
        IF (SELECT count(*) FROM member WHERE nickname = '테스트계정' OR name = '테스트계정') <> 1
            OR NOT EXISTS (
                SELECT 1 FROM member m
                WHERE m.nickname = '테스트계정' AND m.name = '테스트계정'
                  AND m.status = 'ACTIVE' AND m.role = 'USER' AND m.deleted_at IS NULL
                  AND m.onboarding_completed_at IS NOT NULL AND m.join_provider IS NULL
                  AND NOT EXISTS (SELECT 1 FROM social_account s WHERE s.member_id = m.id)
            ) THEN
            RAISE EXCEPTION 'Demo account name conflicts with an existing or unavailable member; no changes applied';
        END IF;
    ELSE
        INSERT INTO member (nickname, name, status, role, onboarding_completed_at)
        VALUES ('테스트계정', '테스트계정', 'ACTIVE', 'USER', CURRENT_TIMESTAMP);
    END IF;
END
$$;

SELECT id AS demo_member_id, status, role, onboarding_completed_at IS NOT NULL AS onboarded
FROM member WHERE nickname = '테스트계정' AND name = '테스트계정';
COMMIT;
