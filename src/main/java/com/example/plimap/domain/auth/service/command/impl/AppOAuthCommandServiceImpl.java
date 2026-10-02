package com.example.plimap.domain.auth.service.command.impl;

import com.example.plimap.domain.auth.dto.AppleDTO;
import com.example.plimap.domain.auth.dto.GoogleDTO;
import com.example.plimap.domain.auth.dto.KakaoDTO;
import com.example.plimap.domain.auth.dto.OAuthDTO;
import com.example.plimap.domain.auth.dto.request.AuthReqDTO;
import com.example.plimap.domain.auth.dto.response.AuthResponse;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.auth.exception.SanctionedMemberAuthenticationException;
import com.example.plimap.domain.auth.service.command.AppOAuthCommandService;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.global.external.kakao.KakaoClientException;
import com.example.plimap.global.external.kakao.KakaoClientTimeoutException;
import com.example.plimap.global.external.kakao.KakaoUserApiClient;
import com.example.plimap.global.external.kakao.KakaoUserInfoResponse;
import com.example.plimap.global.security.AppLoginNonceService;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.RefreshTokenService;
import io.jsonwebtoken.Claims;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppOAuthCommandServiceImpl implements AppOAuthCommandService {

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    // 구글 ID 토큰의 iss는 https://accounts.google.com / accounts.google.com 둘 다 유효하다.
    private static final Set<String> GOOGLE_ISSUERS = Set.of(
            "https://accounts.google.com", "accounts.google.com");
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final Set<String> APPLE_ISSUERS = Set.of("https://appleid.apple.com");

    private final CustomOAuthService customOAuthService;
    private final KakaoUserApiClient kakaoUserApiClient;
    private final IdTokenVerifier idTokenVerifier;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final AppLoginNonceService appLoginNonceService;
    private final MemberRepository memberRepository;
    private final String googleAppClientId;
    private final String appleAppClientId;

    public AppOAuthCommandServiceImpl(
            CustomOAuthService customOAuthService,
            KakaoUserApiClient kakaoUserApiClient,
            IdTokenVerifier idTokenVerifier,
            JwtUtil jwtUtil,
            RefreshTokenService refreshTokenService,
            AppLoginNonceService appLoginNonceService,
            MemberRepository memberRepository,
            @Value("${app-oauth.google.client-id}") String googleAppClientId,
            @Value("${app-oauth.apple.client-id}") String appleAppClientId
    ) {
        this.customOAuthService = customOAuthService;
        this.kakaoUserApiClient = kakaoUserApiClient;
        this.idTokenVerifier = idTokenVerifier;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.appLoginNonceService = appLoginNonceService;
        this.memberRepository = memberRepository;
        this.googleAppClientId = googleAppClientId;
        this.appleAppClientId = appleAppClientId;
    }

    // 의도적으로 @Transactional을 붙이지 않는다: 여기서 직접 하는 일은 JWT 서명(순수 연산)과
    // RefreshTokenService.save()(Redis)뿐이라 JPA 트랜잭션이 필요 없다. DB 쓰기(회원 조회/생성)는
    // customOAuthService.resolveMember()가 자체 @Transactional 경계 안에서 전담한다.
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

    // 웹 로그인의 AuthController.reissue()와 동일한 검증/회전 로직이지만, 쿠키가 아니라
    // 요청 바디로 refreshToken을 받고 응답도 JSON으로 돌려준다(앱은 쿠키를 쓰지 않으므로).
    @Override
    public AuthResponse.AppTokenReissue reissue(AuthReqDTO.AppReissue request) {
        String refreshToken = request.refreshToken();
        if (!jwtUtil.isValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long memberId = jwtUtil.getMemberId(refreshToken);
        Member member = memberRepository.findByIdAndStatusAndDeletedAtIsNull(memberId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        AuthMember authMember = new AuthMember(member);

        String newAccessToken = jwtUtil.createAccessToken(authMember);
        String newRefreshToken = jwtUtil.createRefreshToken(authMember);
        boolean rotated = refreshTokenService.rotateIfMatches(
                memberId,
                jwtUtil.getJti(refreshToken),
                jwtUtil.getJti(newRefreshToken),
                jwtUtil.getRefreshTokenExpiry()
        );
        if (!rotated) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        return new AuthResponse.AppTokenReissue(newAccessToken, newRefreshToken);
    }

    // Apple 로그인 시도 전에 앱이 미리 받아가는 1회용 nonce. Apple 인증 요청에 그대로 실어
    // 보내면 ID 토큰의 nonce claim에 담겨 돌아오고, toAppleDTO()가 로그인 시점에 소비한다.
    @Override
    public AuthResponse.AppLoginNonce issueNonce() {
        return new AuthResponse.AppLoginNonce(appLoginNonceService.issue());
    }

    private OAuthDTO resolveOAuthDTO(AuthReqDTO.AppLogin request) {
        return switch (request.provider()) {
            case KAKAO -> toKakaoDTO(request.token());
            case GOOGLE -> toGoogleDTO(request.token());
            case APPLE -> toAppleDTO(request.token());
        };
    }

    private KakaoDTO toKakaoDTO(String accessToken) {
        KakaoUserInfoResponse response;
        try {
            response = kakaoUserApiClient.getUserInfo(accessToken);
        } catch (KakaoClientTimeoutException exception) {
            // 카카오 서버 응답 지연은 "토큰이 잘못됐다"와 다른 문제라 별도 코드로 구분한다.
            throw new AuthException(AuthErrorCode.APP_LOGIN_PROVIDER_TIMEOUT, exception);
        } catch (KakaoClientException exception) {
            // 만료/위조 등 유효하지 않은 액세스 토큰이면 카카오가 401을 내려주는데, 이 경우도
            // "앱이 보낸 토큰을 검증하지 못했다"는 동일한 의미이므로 통일해서 던진다.
            throw new AuthException(AuthErrorCode.APP_TOKEN_VERIFICATION_FAILED, exception);
        }
        if (response.id() == null || response.kakaoAccount() == null) {
            throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
        }
        KakaoUserInfoResponse.KakaoAccount kakaoAccount = response.kakaoAccount();
        KakaoUserInfoResponse.KakaoAccount.Profile profile = kakaoAccount.profile();
        // 웹 카카오 로그인(CustomOAuthService)과 동일하게, 닉네임 동의를 안 한 프로필은 거부한다.
        if (profile == null || profile.nickname() == null) {
            throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
        }
        // 구글/애플과 동일하게, 카카오가 검증했다고 확인해준 이메일만 사용한다(AdminEmailPolicy
        // 관리자 승격 판단에도 쓰이므로 소유권 미확인 이메일이 그대로 흘러들어가면 안 됨).
        String email = Boolean.TRUE.equals(kakaoAccount.isEmailVerified()) ? kakaoAccount.email() : null;
        return new KakaoDTO(String.valueOf(response.id()), email, profile.nickname());
    }

    private GoogleDTO toGoogleDTO(String idToken) {
        Claims claims = idTokenVerifier.verify(GOOGLE_JWKS_URL, GOOGLE_ISSUERS, googleAppClientId, idToken);
        String providerSubject = claims.getSubject();
        if (providerSubject == null) {
            throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
        }
        String email = verifiedEmail(claims);
        String name = claims.get("name", String.class);
        return new GoogleDTO(providerSubject, email, name);
    }

    private AppleDTO toAppleDTO(String idToken) {
        Claims claims = idTokenVerifier.verify(APPLE_JWKS_URL, APPLE_ISSUERS, appleAppClientId, idToken);
        // 정상 서명된 ID 토큰이라도 탈취되면 만료 전까지 재전송(replay)될 수 있으므로, 로그인
        // 시도마다 서버가 미리 발급한 1회용 nonce가 토큰에 그대로 담겨 왔는지 확인하고 소비한다.
        // 두 번째로 같은 토큰이 들어오면 nonce가 이미 소비돼 있어 거부된다.
        String nonce = claims.get("nonce", String.class);
        if (!appLoginNonceService.consume(nonce)) {
            throw new AuthException(AuthErrorCode.APP_LOGIN_NONCE_INVALID);
        }
        String providerSubject = claims.getSubject();
        if (providerSubject == null) {
            throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
        }
        String email = verifiedEmail(claims);
        return new AppleDTO(providerSubject, email);
    }

    // email_verified가 명시적으로 true인 경우에만 email을 사용한다. 이 값은 AdminEmailPolicy의
    // 관리자 승격 판단에도 쓰이므로, 소유권이 확인되지 않은 이메일로 관리자 이메일을 사칭하는
    // 토큰이 들어와도 관리자 권한이 부여되지 않도록 막기 위함이다. provider-subject 기반인
    // 회원 식별 자체에는 영향 없음(이메일이 없어도 신규/기존 회원 판별은 정상 동작).
    private String verifiedEmail(Claims claims) {
        Object emailVerified = claims.get("email_verified");
        boolean verified = switch (emailVerified) {
            case Boolean bool -> bool;
            case String str -> Boolean.parseBoolean(str);
            case null, default -> false;
        };
        return verified ? claims.get("email", String.class) : null;
    }
}
