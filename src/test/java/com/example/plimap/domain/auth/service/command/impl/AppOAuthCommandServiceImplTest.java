package com.example.plimap.domain.auth.service.command.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.plimap.domain.auth.dto.OAuthDTO;
import com.example.plimap.domain.auth.dto.request.AuthReqDTO;
import com.example.plimap.domain.auth.dto.response.AuthResponse;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.auth.enums.AuthProvider;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.auth.exception.SanctionedMemberAuthenticationException;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.global.external.kakao.KakaoClientException;
import com.example.plimap.global.external.kakao.KakaoUserApiClient;
import com.example.plimap.global.external.kakao.KakaoUserInfoResponse;
import com.example.plimap.global.security.AppLoginNonceService;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.RefreshTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppOAuthCommandServiceImplTest {

    private final CustomOAuthService customOAuthService = mock(CustomOAuthService.class);
    private final KakaoUserApiClient kakaoUserApiClient = mock(KakaoUserApiClient.class);
    private final IdTokenVerifier idTokenVerifier = mock(IdTokenVerifier.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AppLoginNonceService appLoginNonceService = mock(AppLoginNonceService.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);

    private final AppOAuthCommandServiceImpl service = new AppOAuthCommandServiceImpl(
            customOAuthService, kakaoUserApiClient, idTokenVerifier, jwtUtil, refreshTokenService,
            appLoginNonceService, memberRepository, "google-app-client-id", "apple-app-client-id"
    );

    @Test
    void 카카오_액세스_토큰으로_로그인하면_회원을_조회하고_토큰을_발급한다() {
        KakaoUserInfoResponse response = new KakaoUserInfoResponse(
                12345L,
                new KakaoUserInfoResponse.KakaoAccount(
                        "user@kakao.com",
                        true,
                        new KakaoUserInfoResponse.KakaoAccount.Profile("닉네임")
                )
        );
        when(kakaoUserApiClient.getUserInfo("kakao-access-token")).thenReturn(response);

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.isOnboarded()).thenReturn(true);

        ArgumentCaptor<OAuthDTO> dtoCaptor = ArgumentCaptor.forClass(OAuthDTO.class);
        when(customOAuthService.resolveMember(eq(AuthProvider.KAKAO), dtoCaptor.capture())).thenReturn(member);

        when(jwtUtil.createAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.createRefreshToken(any())).thenReturn("refresh-token");
        when(jwtUtil.getJti("refresh-token")).thenReturn("jti-1");
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(14));

        AuthResponse.AppLogin result = service.login(new AuthReqDTO.AppLogin(AuthProvider.KAKAO, "kakao-access-token"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(dtoCaptor.getValue().getProviderSubject()).isEqualTo("12345");
        assertThat(dtoCaptor.getValue().getEmail()).isEqualTo("user@kakao.com");
        verify(refreshTokenService).save(1L, "jti-1", Duration.ofDays(14));
    }

    @Test
    void 카카오_이메일이_검증되지_않았으면_이메일을_회원조회에_넘기지_않는다() {
        // given - 카카오가 검증했다고 확인해주지 않은 이메일은 구글/애플과 동일하게 소유권이
        // 확인되지 않은 것으로 취급한다(AdminEmailPolicy 등 이메일 기반 판단에 오용되면 안 됨).
        KakaoUserInfoResponse response = new KakaoUserInfoResponse(
                99L,
                new KakaoUserInfoResponse.KakaoAccount(
                        "unverified@kakao.com", false, new KakaoUserInfoResponse.KakaoAccount.Profile("닉네임"))
        );
        when(kakaoUserApiClient.getUserInfo(anyString())).thenReturn(response);

        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(true);
        ArgumentCaptor<OAuthDTO> dtoCaptor = ArgumentCaptor.forClass(OAuthDTO.class);
        when(customOAuthService.resolveMember(eq(AuthProvider.KAKAO), dtoCaptor.capture())).thenReturn(member);
        when(jwtUtil.createAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.createRefreshToken(any())).thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(14));

        // when
        service.login(new AuthReqDTO.AppLogin(AuthProvider.KAKAO, "kakao-access-token"));

        // then
        assertThat(dtoCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void 카카오_토큰_검증에_실패하면_500이_아니라_APP_TOKEN_VERIFICATION_FAILED로_변환된다() {
        // given - 만료/위조된 액세스 토큰이면 카카오 유저 API 호출 자체가 401로 실패한다
        when(kakaoUserApiClient.getUserInfo(anyString()))
                .thenThrow(new KakaoClientException("Kakao User API request failed"));

        // when & then
        assertThatThrownBy(() -> service.login(new AuthReqDTO.AppLogin(AuthProvider.KAKAO, "invalid-token")))
                .isInstanceOf(AuthException.class);
        verifyNoInteractions(customOAuthService, jwtUtil, refreshTokenService);
    }

    @Test
    void 정지된_회원이_로그인하면_토큰_없이_제재_정보만_반환한다() {
        KakaoUserInfoResponse response = new KakaoUserInfoResponse(
                1L,
                new KakaoUserInfoResponse.KakaoAccount(
                        "a@b.com", true, new KakaoUserInfoResponse.KakaoAccount.Profile("n"))
        );
        when(kakaoUserApiClient.getUserInfo(anyString())).thenReturn(response);

        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.SUSPENDED);
        when(member.getSuspendedUntil()).thenReturn(Instant.now().plusSeconds(3600));
        // 예외는 when(...) 체인 밖에서 미리 만들어야 한다 - 인라인으로 넘기면 예외 생성자가
        // member(다른 mock)의 getter를 호출해서 Mockito의 스터빙 상태 추적이 꼬인다.
        SanctionedMemberAuthenticationException sanctioned = new SanctionedMemberAuthenticationException(member);
        when(customOAuthService.resolveMember(any(), any())).thenThrow(sanctioned);

        AuthResponse.AppLogin result = service.login(new AuthReqDTO.AppLogin(AuthProvider.KAKAO, "kakao-access-token"));

        assertThat(result.accessToken()).isNull();
        assertThat(result.refreshToken()).isNull();
        assertThat(result.status()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(result.suspendedUntil()).isNotNull();
        verifyNoInteractions(jwtUtil, refreshTokenService);
    }

    @Test
    void 구글_이메일이_검증된_경우에만_이메일을_회원조회에_사용한다() {
        // given
        Claims claims = Jwts.claims(Map.of(
                "sub", "google-subject-verified",
                "email", "user@gmail.com",
                "email_verified", true
        ));
        when(idTokenVerifier.verify(anyString(), any(), anyString(), eq("verified-token"))).thenReturn(claims);

        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(true);
        ArgumentCaptor<OAuthDTO> dtoCaptor = ArgumentCaptor.forClass(OAuthDTO.class);
        when(customOAuthService.resolveMember(eq(AuthProvider.GOOGLE), dtoCaptor.capture())).thenReturn(member);
        when(jwtUtil.createAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.createRefreshToken(any())).thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(14));

        // when
        service.login(new AuthReqDTO.AppLogin(AuthProvider.GOOGLE, "verified-token"));

        // then
        assertThat(dtoCaptor.getValue().getEmail()).isEqualTo("user@gmail.com");
    }

    @Test
    void 구글_이메일이_검증되지_않았으면_이메일을_회원조회에_넘기지_않는다() {
        // given - email_verified가 false인 이메일은 소유권이 확인되지 않았으므로
        // AdminEmailPolicy 등 이메일 기반 판단에 잘못 쓰이지 않도록 아예 넘기지 않는다.
        Claims claims = Jwts.claims(Map.of(
                "sub", "google-subject-unverified",
                "email", "unverified@gmail.com",
                "email_verified", false
        ));
        when(idTokenVerifier.verify(anyString(), any(), anyString(), eq("unverified-token"))).thenReturn(claims);

        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(true);
        ArgumentCaptor<OAuthDTO> dtoCaptor = ArgumentCaptor.forClass(OAuthDTO.class);
        when(customOAuthService.resolveMember(eq(AuthProvider.GOOGLE), dtoCaptor.capture())).thenReturn(member);
        when(jwtUtil.createAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.createRefreshToken(any())).thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(14));

        // when
        service.login(new AuthReqDTO.AppLogin(AuthProvider.GOOGLE, "unverified-token"));

        // then
        assertThat(dtoCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void 애플_ID_토큰은_email_verified_클레임이_없어도_이메일_없이_로그인된다() {
        // given - 재로그인 시 Apple은 email/email_verified를 아예 내려주지 않을 수 있다
        Claims claims = Jwts.claims(Map.of("sub", "apple-subject-1", "nonce", "nonce-value"));
        when(idTokenVerifier.verify(anyString(), any(), anyString(), eq("apple-token"))).thenReturn(claims);
        when(appLoginNonceService.consume("nonce-value")).thenReturn(true);

        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(true);
        ArgumentCaptor<OAuthDTO> dtoCaptor = ArgumentCaptor.forClass(OAuthDTO.class);
        when(customOAuthService.resolveMember(eq(AuthProvider.APPLE), dtoCaptor.capture())).thenReturn(member);
        when(jwtUtil.createAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.createRefreshToken(any())).thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(14));

        // when
        AuthResponse.AppLogin result = service.login(new AuthReqDTO.AppLogin(AuthProvider.APPLE, "apple-token"));

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(dtoCaptor.getValue().getProviderSubject()).isEqualTo("apple-subject-1");
        assertThat(dtoCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void 애플_로그인은_토큰의_nonce를_소비해서_재전송을_막는다() {
        // given - nonce가 없거나(null) 이미 소비됐으면 재전송된 토큰으로 간주해 거부한다
        Claims claims = Jwts.claims(Map.of("sub", "apple-subject-1"));
        when(idTokenVerifier.verify(anyString(), any(), anyString(), eq("replayed-token"))).thenReturn(claims);
        when(appLoginNonceService.consume(any())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.login(new AuthReqDTO.AppLogin(AuthProvider.APPLE, "replayed-token")))
                .isInstanceOf(AuthException.class);
        verifyNoInteractions(customOAuthService, jwtUtil, refreshTokenService);
    }

    @Test
    void 구글_ID_토큰에_sub이_없으면_INVALID_SOCIAL_PROFILE_예외가_발생한다() {
        // given
        Claims claims = Jwts.claims(Map.of("email", "user@gmail.com"));
        when(idTokenVerifier.verify(anyString(), any(), anyString(), eq("no-sub-token"))).thenReturn(claims);

        // when & then
        assertThatThrownBy(() -> service.login(new AuthReqDTO.AppLogin(AuthProvider.GOOGLE, "no-sub-token")))
                .isInstanceOf(MemberException.class);
        verifyNoInteractions(customOAuthService, jwtUtil, refreshTokenService);
    }

    @Test
    void 애플_ID_토큰에_sub이_없으면_INVALID_SOCIAL_PROFILE_예외가_발생한다() {
        // given
        Claims claims = Jwts.claims(Map.of("nonce", "nonce-value"));
        when(idTokenVerifier.verify(anyString(), any(), anyString(), eq("no-sub-token"))).thenReturn(claims);
        when(appLoginNonceService.consume("nonce-value")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> service.login(new AuthReqDTO.AppLogin(AuthProvider.APPLE, "no-sub-token")))
                .isInstanceOf(MemberException.class);
        verifyNoInteractions(customOAuthService, jwtUtil, refreshTokenService);
    }

    @Test
    void nonce를_발급하면_AppLoginNonceService가_생성한_값을_그대로_반환한다() {
        // given
        when(appLoginNonceService.issue()).thenReturn("issued-nonce");

        // when
        AuthResponse.AppLoginNonce result = service.issueNonce();

        // then
        assertThat(result.nonce()).isEqualTo("issued-nonce");
    }

    @Test
    void 유효한_리프레시_토큰으로_재발급하면_새_토큰_쌍을_JSON으로_반환한다() {
        // given
        String refreshToken = "valid-refresh-token";
        when(jwtUtil.isValid(refreshToken)).thenReturn(true);
        when(jwtUtil.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtil.getMemberId(refreshToken)).thenReturn(1L);
        when(jwtUtil.getJti(refreshToken)).thenReturn("current-jti");

        Member member = mock(Member.class);
        when(memberRepository.findByIdAndStatusAndDeletedAtIsNull(1L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));

        when(jwtUtil.createAccessToken(any(AuthMember.class))).thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken(any(AuthMember.class))).thenReturn("new-refresh-token");
        when(jwtUtil.getJti("new-refresh-token")).thenReturn("new-jti");
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(14));
        when(refreshTokenService.rotateIfMatches(1L, "current-jti", "new-jti", Duration.ofDays(14)))
                .thenReturn(true);

        // when
        AuthResponse.AppTokenReissue result = service.reissue(new AuthReqDTO.AppReissue(refreshToken));

        // then
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void 유효하지_않은_리프레시_토큰이면_재발급에_실패한다() {
        // given
        when(jwtUtil.isValid("invalid-refresh-token")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.reissue(new AuthReqDTO.AppReissue("invalid-refresh-token")))
                .isInstanceOf(AuthException.class);
        verify(memberRepository, never()).findByIdAndStatusAndDeletedAtIsNull(any(), any());
    }

    @Test
    void 저장된_JTI와_다르면_재발급이_거부된다() {
        // given - 다른 기기 등에서 이미 회전돼서 이 refreshToken은 더 이상 최신이 아닌 상황
        String refreshToken = "stale-refresh-token";
        when(jwtUtil.isValid(refreshToken)).thenReturn(true);
        when(jwtUtil.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtil.getMemberId(refreshToken)).thenReturn(1L);
        when(jwtUtil.getJti(refreshToken)).thenReturn("stale-jti");

        Member member = mock(Member.class);
        when(memberRepository.findByIdAndStatusAndDeletedAtIsNull(1L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(jwtUtil.createAccessToken(any(AuthMember.class))).thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken(any(AuthMember.class))).thenReturn("new-refresh-token");
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(14));
        when(refreshTokenService.rotateIfMatches(eq(1L), eq("stale-jti"), any(), eq(Duration.ofDays(14))))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.reissue(new AuthReqDTO.AppReissue(refreshToken)))
                .isInstanceOf(AuthException.class);
    }
}
