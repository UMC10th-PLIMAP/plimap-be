package com.example.plimap.domain.auth.service.command.impl;

import com.example.plimap.domain.auth.dto.AppleDTO;
import com.example.plimap.domain.auth.dto.GoogleDTO;
import com.example.plimap.domain.auth.dto.KakaoDTO;
import com.example.plimap.domain.auth.dto.OAuthDTO;
import com.example.plimap.domain.auth.dto.request.AuthReqDTO;
import com.example.plimap.domain.auth.dto.response.AuthResponse;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.auth.exception.SanctionedMemberAuthenticationException;
import com.example.plimap.domain.auth.service.command.AppOAuthCommandService;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.global.external.kakao.KakaoUserApiClient;
import com.example.plimap.global.external.kakao.KakaoUserInfoResponse;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.RefreshTokenService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppOAuthCommandServiceImpl implements AppOAuthCommandService {

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final CustomOAuthService customOAuthService;
    private final KakaoUserApiClient kakaoUserApiClient;
    private final IdTokenVerifier idTokenVerifier;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final String googleAppClientId;
    private final String appleAppClientId;

    public AppOAuthCommandServiceImpl(
            CustomOAuthService customOAuthService,
            KakaoUserApiClient kakaoUserApiClient,
            IdTokenVerifier idTokenVerifier,
            JwtUtil jwtUtil,
            RefreshTokenService refreshTokenService,
            @Value("${app-oauth.google.client-id}") String googleAppClientId,
            @Value("${app-oauth.apple.client-id}") String appleAppClientId
    ) {
        this.customOAuthService = customOAuthService;
        this.kakaoUserApiClient = kakaoUserApiClient;
        this.idTokenVerifier = idTokenVerifier;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.googleAppClientId = googleAppClientId;
        this.appleAppClientId = appleAppClientId;
    }

    @Override
    public AuthResponse.AppLogin login(AuthReqDTO.AppLogin request) {
        OAuthDTO dto = resolveOAuthDTO(request);

        Member member;
        try {
            member = customOAuthService.resolveMember(request.provider(), dto);
        } catch (SanctionedMemberAuthenticationException exception) {
            return AuthResponse.AppLogin.sanctioned(exception);
        }

        AuthMember authMember = new AuthMember(member);
        String accessToken = jwtUtil.createAccessToken(authMember);
        String refreshToken = jwtUtil.createRefreshToken(authMember);
        refreshTokenService.save(member.getId(), jwtUtil.getJti(refreshToken), jwtUtil.getRefreshTokenExpiry());

        boolean isNewUser = !member.isOnboarded();
        return AuthResponse.AppLogin.of(accessToken, refreshToken, isNewUser);
    }

    private OAuthDTO resolveOAuthDTO(AuthReqDTO.AppLogin request) {
        return switch (request.provider()) {
            case KAKAO -> toKakaoDTO(request.token());
            case GOOGLE -> toGoogleDTO(request.token());
            case APPLE -> toAppleDTO(request.token());
        };
    }

    private KakaoDTO toKakaoDTO(String accessToken) {
        KakaoUserInfoResponse response = kakaoUserApiClient.getUserInfo(accessToken);
        if (response.id() == null || response.kakaoAccount() == null) {
            throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
        }
        KakaoUserInfoResponse.KakaoAccount.Profile profile = response.kakaoAccount().profile();
        String nickname = profile != null ? profile.nickname() : null;
        return new KakaoDTO(String.valueOf(response.id()), response.kakaoAccount().email(), nickname);
    }

    private GoogleDTO toGoogleDTO(String idToken) {
        Claims claims = idTokenVerifier.verify(GOOGLE_JWKS_URL, GOOGLE_ISSUER, googleAppClientId, idToken);
        String providerSubject = claims.getSubject();
        if (providerSubject == null) {
            throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
        }
        String email = claims.get("email", String.class);
        String name = claims.get("name", String.class);
        return new GoogleDTO(providerSubject, email, name);
    }

    private AppleDTO toAppleDTO(String idToken) {
        Claims claims = idTokenVerifier.verify(APPLE_JWKS_URL, APPLE_ISSUER, appleAppClientId, idToken);
        String providerSubject = claims.getSubject();
        if (providerSubject == null) {
            throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
        }
        String email = claims.get("email", String.class);
        return new AppleDTO(providerSubject, email);
    }
}
