package com.example.plimap.domain.auth.service.command.impl;

import com.example.plimap.domain.auth.entity.SocialAccount;
import com.example.plimap.domain.auth.enums.AuthProvider;
import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.auth.repository.SocialAccountRepository;
import com.example.plimap.domain.auth.service.command.SocialAccountCommandService;
import com.example.plimap.global.external.apple.AppleAuthClient;
import com.example.plimap.global.external.google.GoogleAuthClient;
import com.example.plimap.global.external.google.GoogleAuthProperties;
import com.example.plimap.global.external.kakao.KakaoClientException;
import com.example.plimap.global.external.kakao.KakaoUserApiClient;
import com.example.plimap.global.external.oidc.OidcTokenResponse;
import com.example.plimap.global.external.oidc.SocialAuthClientException;
import com.example.plimap.global.security.SocialTokenCipher;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SocialAccountCommandServiceImpl implements SocialAccountCommandService {

    private final SocialAccountRepository socialAccountRepository;
    private final AppleAuthClient appleAuthClient;
    private final GoogleAuthClient googleAuthClient;
    private final KakaoUserApiClient kakaoUserApiClient;
    private final IdTokenVerifier idTokenVerifier;
    private final SocialTokenCipher socialTokenCipher;
    private final String appleClientId;
    private final String googleClientId;

    public SocialAccountCommandServiceImpl(
            SocialAccountRepository socialAccountRepository,
            AppleAuthClient appleAuthClient,
            GoogleAuthClient googleAuthClient,
            KakaoUserApiClient kakaoUserApiClient,
            IdTokenVerifier idTokenVerifier,
            SocialTokenCipher socialTokenCipher,
            @Value("${app-oauth.apple.client-id}") String appleClientId,
            GoogleAuthProperties googleAuthProperties
    ) {
        this.socialAccountRepository = socialAccountRepository;
        this.appleAuthClient = appleAuthClient;
        this.googleAuthClient = googleAuthClient;
        this.kakaoUserApiClient = kakaoUserApiClient;
        this.idTokenVerifier = idTokenVerifier;
        this.socialTokenCipher = socialTokenCipher;
        this.appleClientId = appleClientId;
        this.googleClientId = googleAuthProperties.clientId();
    }

    @Override
    @Transactional
    public void deleteByMemberId(Long memberId) {
        socialAccountRepository.deleteByMemberId(memberId);
    }

    // 외부 API 호출 동안 DB 커넥션을 잡지 않도록 트랜잭션 없이 교환한 뒤 save()로 반영한다.
    @Override
    public void saveRefreshTokenByCode(Long memberId, AuthProvider provider, String authorizationCode) {
        SocialAccount account = socialAccountRepository.findFirstByMember_Id(memberId).orElse(null);
        if (account == null || account.getProvider() != provider) {
            return;
        }
        try {
            String refreshToken = exchangeForAccount(account, authorizationCode);
            account.updateEncryptedRefreshToken(socialTokenCipher.encrypt(refreshToken));
            socialAccountRepository.save(account);
        } catch (RuntimeException exception) {
            log.warn("소셜 refresh token 저장 실패 memberId={}, provider={}", memberId, provider, exception);
        }
    }

    @Override
    @Transactional
    public void saveRefreshToken(Long memberId, String refreshToken) {
        socialAccountRepository.findFirstByMember_Id(memberId)
                .ifPresent(account -> account.updateEncryptedRefreshToken(socialTokenCipher.encrypt(refreshToken)));
    }

    @Override
    @Transactional
    public void unlink(Long memberId, String reauthCode) {
        SocialAccount account = socialAccountRepository.findFirstByMember_Id(memberId).orElse(null);
        if (account == null) {
            return;
        }
        if (account.getProvider() == AuthProvider.KAKAO) {
            unlinkKakao(account);
            return;
        }

        String refreshToken = reauthCode != null && !reauthCode.isBlank()
                ? exchangeForReauth(account, reauthCode)
                : storedRefreshToken(account);
        if (refreshToken == null || !revoke(account.getProvider(), refreshToken)) {
            throw reauthRequired(account.getProvider());
        }
        account.updateEncryptedRefreshToken(null);
    }

    @Override
    @Transactional
    public void unlinkQuietly(Long memberId) {
        try {
            unlink(memberId, null);
        } catch (RuntimeException exception) {
            log.warn("자동 탈퇴 소셜 연결 끊기 실패 memberId={}", memberId, exception);
        }
    }

    // 카카오는 어드민 키로 끊어서 사용자가 다시 로그인해도 해결되지 않는다. 실패는 로그만 남기고 탈퇴를 진행한다.
    private void unlinkKakao(SocialAccount account) {
        try {
            kakaoUserApiClient.unlink(account.getProviderSubject());
        } catch (KakaoClientException exception) {
            log.warn("카카오 연결 끊기 실패 socialAccountId={}", account.getId(), exception);
        }
    }

    private String storedRefreshToken(SocialAccount account) {
        String encrypted = account.getEncryptedRefreshToken();
        return encrypted == null ? null : socialTokenCipher.decrypt(encrypted);
    }

    private boolean revoke(AuthProvider provider, String refreshToken) {
        try {
            return provider == AuthProvider.APPLE
                    ? appleAuthClient.revoke(refreshToken)
                    : googleAuthClient.revoke(refreshToken);
        } catch (SocialAuthClientException exception) {
            throw new AuthException(AuthErrorCode.SOCIAL_UNLINK_FAILED, exception);
        }
    }

    // 재로그인 code가 잘못됐거나 다른 계정의 것이면 다시 재로그인을 요청한다.
    private String exchangeForReauth(SocialAccount account, String reauthCode) {
        try {
            return exchangeForAccount(account, reauthCode);
        } catch (SocialAuthClientException | AuthException exception) {
            throw reauthRequired(account.getProvider());
        }
    }

    // code를 교환한 뒤 함께 받은 ID 토큰의 sub가 이 계정과 같은지 확인한다. 다른 계정의 토큰을
    // 저장하거나 끊지 않기 위함이다.
    private String exchangeForAccount(SocialAccount account, String code) {
        boolean apple = account.getProvider() == AuthProvider.APPLE;
        OidcTokenResponse response = apple ? appleAuthClient.exchangeCode(code) : googleAuthClient.exchangeCode(code);
        Claims claims = apple
                ? idTokenVerifier.verify(AppOAuthCommandServiceImpl.APPLE_JWKS_URL,
                        AppOAuthCommandServiceImpl.APPLE_ISSUERS, appleClientId, response.idToken())
                : idTokenVerifier.verify(AppOAuthCommandServiceImpl.GOOGLE_JWKS_URL,
                        AppOAuthCommandServiceImpl.GOOGLE_ISSUERS, googleClientId, response.idToken());
        if (!account.getProviderSubject().equals(claims.getSubject())) {
            throw new AuthException(AuthErrorCode.APP_TOKEN_VERIFICATION_FAILED);
        }
        return response.refreshToken();
    }

    private static AuthException reauthRequired(AuthProvider provider) {
        return new AuthException(provider == AuthProvider.APPLE
                ? AuthErrorCode.APPLE_REAUTH_REQUIRED
                : AuthErrorCode.GOOGLE_REAUTH_REQUIRED);
    }
}
