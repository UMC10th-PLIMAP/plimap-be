package com.example.plimap.domain.auth.service.command.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.plimap.domain.auth.entity.SocialAccount;
import com.example.plimap.domain.auth.enums.AuthProvider;
import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.auth.repository.SocialAccountRepository;
import com.example.plimap.global.external.apple.AppleAuthClient;
import com.example.plimap.global.external.google.GoogleAuthClient;
import com.example.plimap.global.external.google.GoogleAuthProperties;
import com.example.plimap.global.external.kakao.KakaoClientException;
import com.example.plimap.global.external.kakao.KakaoUserApiClient;
import com.example.plimap.global.external.oidc.OidcTokenResponse;
import com.example.plimap.global.external.oidc.SocialAuthClientException;
import com.example.plimap.global.security.SocialTokenCipher;
import io.jsonwebtoken.Jwts;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SocialAccountCommandServiceImplTest {

    private static final Long MEMBER_ID = 1L;

    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
    private final GoogleAuthClient googleAuthClient = mock(GoogleAuthClient.class);
    private final KakaoUserApiClient kakaoUserApiClient = mock(KakaoUserApiClient.class);
    private final IdTokenVerifier idTokenVerifier = mock(IdTokenVerifier.class);
    private final SocialTokenCipher cipher =
            new SocialTokenCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    private final SocialAccountCommandServiceImpl service = new SocialAccountCommandServiceImpl(
            socialAccountRepository, appleAuthClient, googleAuthClient, kakaoUserApiClient,
            idTokenVerifier, cipher, "apple-client-id",
            new GoogleAuthProperties("google-client-id", "google-client-secret")
    );

    @Test
    void 카카오_회원은_어드민_키로_연결을_끊는다() {
        givenAccount(AuthProvider.KAKAO, "12345", null);

        service.unlink(MEMBER_ID, null);

        verify(kakaoUserApiClient).unlink("12345");
    }

    @Test
    void 카카오_연결_끊기가_실패해도_탈퇴를_막지_않는다() {
        givenAccount(AuthProvider.KAKAO, "12345", null);
        doThrow(new KakaoClientException("fail")).when(kakaoUserApiClient).unlink("12345");

        assertThatCode(() -> service.unlink(MEMBER_ID, null)).doesNotThrowAnyException();
    }

    @Test
    void 애플_회원은_저장된_토큰으로_revoke하고_토큰을_지운다() {
        SocialAccount account = givenAccount(AuthProvider.APPLE, "apple-sub", cipher.encrypt("apple-refresh"));
        when(appleAuthClient.revoke("apple-refresh")).thenReturn(true);

        service.unlink(MEMBER_ID, null);

        verify(appleAuthClient).revoke("apple-refresh");
        assertThat(account.getEncryptedRefreshToken()).isNull();
    }

    @Test
    void 저장된_토큰이_없으면_재로그인을_요청한다() {
        givenAccount(AuthProvider.APPLE, "apple-sub", null);

        assertReauthRequired(AuthErrorCode.APPLE_REAUTH_REQUIRED);
        verify(appleAuthClient, never()).revoke(any());
    }

    @Test
    void provider가_토큰을_거부하면_재로그인을_요청한다() {
        givenAccount(AuthProvider.GOOGLE, "google-sub", cipher.encrypt("expired"));
        when(googleAuthClient.revoke("expired")).thenReturn(false);

        assertReauthRequired(AuthErrorCode.GOOGLE_REAUTH_REQUIRED);
    }

    @Test
    void revoke_요청이_네트워크_오류로_실패하면_재시도_가능한_예외를_던진다() {
        givenAccount(AuthProvider.APPLE, "apple-sub", cipher.encrypt("apple-refresh"));
        when(appleAuthClient.revoke("apple-refresh")).thenThrow(new SocialAuthClientException("timeout"));

        assertThatThrownBy(() -> service.unlink(MEMBER_ID, null))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.SOCIAL_UNLINK_FAILED));
    }

    @Test
    void 재로그인_code가_있으면_교환한_토큰으로_revoke한다() {
        givenAccount(AuthProvider.GOOGLE, "google-sub", null);
        givenExchange("reauth-code", "google-sub", "new-refresh");
        when(googleAuthClient.revoke("new-refresh")).thenReturn(true);

        service.unlink(MEMBER_ID, "reauth-code");

        verify(googleAuthClient).revoke("new-refresh");
    }

    @Test
    void 재로그인_code가_다른_계정의_것이면_다시_재로그인을_요청한다() {
        givenAccount(AuthProvider.GOOGLE, "google-sub", null);
        givenExchange("other-code", "other-sub", "other-refresh");

        assertThatThrownBy(() -> service.unlink(MEMBER_ID, "other-code"))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.GOOGLE_REAUTH_REQUIRED));
        verify(googleAuthClient, never()).revoke(any());
    }

    @Test
    void 자동_탈퇴는_연결을_끊지_못해도_예외를_던지지_않는다() {
        givenAccount(AuthProvider.APPLE, "apple-sub", null);

        assertThatCode(() -> service.unlinkQuietly(MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    void 로그인_code를_교환해_암호화된_refresh_token을_저장한다() {
        SocialAccount account = givenAccount(AuthProvider.GOOGLE, "google-sub", null);
        givenExchange("login-code", "google-sub", "google-refresh");

        service.saveRefreshTokenByCode(MEMBER_ID, AuthProvider.GOOGLE, "login-code");

        assertThat(account.getEncryptedRefreshToken()).isNotEqualTo("google-refresh");
        assertThat(cipher.decrypt(account.getEncryptedRefreshToken())).isEqualTo("google-refresh");
        verify(socialAccountRepository).save(account);
    }

    @Test
    void 로그인_code_교환에_실패해도_예외를_던지지_않는다() {
        givenAccount(AuthProvider.APPLE, "apple-sub", null);
        when(appleAuthClient.exchangeCode("bad-code")).thenThrow(new SocialAuthClientException("invalid_grant"));

        assertThatCode(() -> service.saveRefreshTokenByCode(MEMBER_ID, AuthProvider.APPLE, "bad-code"))
                .doesNotThrowAnyException();
        verify(socialAccountRepository, never()).save(any());
    }

    private SocialAccount givenAccount(AuthProvider provider, String subject, String encryptedRefreshToken) {
        SocialAccount account = SocialAccount.create(null, provider, subject, null);
        account.updateEncryptedRefreshToken(encryptedRefreshToken);
        when(socialAccountRepository.findFirstByMember_Id(MEMBER_ID)).thenReturn(Optional.of(account));
        return account;
    }

    private void givenExchange(String code, String subject, String refreshToken) {
        OidcTokenResponse response = new OidcTokenResponse(refreshToken, code + "-id-token");
        when(googleAuthClient.exchangeCode(code)).thenReturn(response);
        when(appleAuthClient.exchangeCode(code)).thenReturn(response);
        when(idTokenVerifier.verify(anyString(), any(), anyString(), eq(code + "-id-token")))
                .thenReturn(Jwts.claims(Map.of("sub", subject)));
    }

    private void assertReauthRequired(AuthErrorCode expected) {
        assertThatThrownBy(() -> service.unlink(MEMBER_ID, null))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
