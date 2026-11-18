-- 탈퇴 시 애플/구글 연결을 끊기(revoke) 위한 provider refresh token. 앱 레벨에서 AES-GCM으로 암호화해 저장한다.
-- 카카오는 어드민 키로 연결을 끊으므로 저장하지 않는다.
ALTER TABLE social_account
    ADD COLUMN encrypted_refresh_token TEXT;
