package com.example.plimap.domain.auth.controller;

import com.example.plimap.domain.auth.dto.response.AuthResponse;
import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.auth.service.command.AppOAuthCommandService;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.member.service.command.MemberCommandService;
import com.example.plimap.domain.member.service.command.TermsCommandService;
import com.example.plimap.domain.member.service.query.TermsQueryService;
import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.security.AuthCookieUtil;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.RefreshTokenService;
import com.example.plimap.global.security.SessionInvalidationService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final SessionInvalidationService sessionInvalidationService = mock(SessionInvalidationService.class);
    private final AuthCookieUtil authCookieUtil = mock(AuthCookieUtil.class);
    private final AppOAuthCommandService appOAuthCommandService = mock(AppOAuthCommandService.class);

    private final AuthController controller = new AuthController(
            mock(MemberCommandService.class),
            mock(TermsQueryService.class),
            mock(TermsCommandService.class),
            memberRepository,
            jwtUtil,
            refreshTokenService,
            authCookieUtil,
            sessionInvalidationService,
            appOAuthCommandService
    );

    @Test
    void CSRF_토큰을_응답_본문으로_반환한다() {
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("masked-csrf-token");

        ApiResponse<AuthResponse.CsrfToken> response = controller.getCsrfToken(csrfToken);

        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("AUTH_CSRF_TOKEN_ISSUED_SUCCESS");
        assertThat(response.getResult().token()).isEqualTo("masked-csrf-token");
    }

    @Test
    void 로그아웃하면_세션_무효화를_위임한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<Void> apiResponse = controller.logout(request, response);

        assertThat(apiResponse.getIsSuccess()).isTrue();
        assertThat(apiResponse.getCode()).isEqualTo("MEMBER_LOGOUT_SUCCESS");
        verify(sessionInvalidationService).invalidate(request, response);
    }

    @Test
    void 탈퇴한_회원의_리프레시_토큰이면_재발급을_거부한다() {
        String refreshToken = "refresh-token";
        when(jwtUtil.isValid(refreshToken)).thenReturn(true);
        when(jwtUtil.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtil.getMemberId(refreshToken)).thenReturn(1L);
        when(memberRepository.findByIdAndStatusAndDeletedAtIsNull(1L, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", refreshToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void 유효한_리프레시_토큰이면_JTI를_원자적으로_회전하고_쿠키를_갱신한다() {
        String refreshToken = "refresh-token";
        String newAccessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token";
        Duration accessTokenExpiry = Duration.ofDays(1);
        Duration refreshTokenExpiry = Duration.ofDays(14);
        Member member = mock(Member.class);

        when(jwtUtil.isValid(refreshToken)).thenReturn(true);
        when(jwtUtil.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtil.getMemberId(refreshToken)).thenReturn(1L);
        when(memberRepository.findByIdAndStatusAndDeletedAtIsNull(1L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(jwtUtil.createAccessToken(any())).thenReturn(newAccessToken);
        when(jwtUtil.createRefreshToken(any())).thenReturn(newRefreshToken);
        when(jwtUtil.getJti(refreshToken)).thenReturn("current-refresh-jti");
        when(jwtUtil.getJti(newRefreshToken)).thenReturn("new-refresh-jti");
        when(jwtUtil.getAccessTokenExpiry()).thenReturn(accessTokenExpiry);
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(refreshTokenExpiry);
        when(refreshTokenService.rotateIfMatches(
                1L,
                "current-refresh-jti",
                "new-refresh-jti",
                refreshTokenExpiry
        )).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", refreshToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<Void> apiResponse = controller.reissue(request, response);

        assertThat(apiResponse.getIsSuccess()).isTrue();
        assertThat(apiResponse.getCode()).isEqualTo("AUTH_TOKEN_REISSUED_SUCCESS");
        verify(refreshTokenService).rotateIfMatches(
                1L,
                "current-refresh-jti",
                "new-refresh-jti",
                refreshTokenExpiry
        );
        verify(authCookieUtil).setCookie(response, "accessToken", newAccessToken, accessTokenExpiry);
        verify(authCookieUtil).setCookie(response, "refreshToken", newRefreshToken, refreshTokenExpiry);
    }

    @Test
    void 이미_소비된_리프레시_토큰이면_새_쿠키를_발급하지_않는다() {
        String refreshToken = "refresh-token";
        String newRefreshToken = "new-refresh-token";
        Duration refreshTokenExpiry = Duration.ofDays(14);
        Member member = mock(Member.class);

        when(jwtUtil.isValid(refreshToken)).thenReturn(true);
        when(jwtUtil.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtUtil.getMemberId(refreshToken)).thenReturn(1L);
        when(memberRepository.findByIdAndStatusAndDeletedAtIsNull(1L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(jwtUtil.createAccessToken(any())).thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken(any())).thenReturn(newRefreshToken);
        when(jwtUtil.getJti(refreshToken)).thenReturn("consumed-refresh-jti");
        when(jwtUtil.getJti(newRefreshToken)).thenReturn("unused-refresh-jti");
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(refreshTokenExpiry);
        when(refreshTokenService.rotateIfMatches(
                1L,
                "consumed-refresh-jti",
                "unused-refresh-jti",
                refreshTokenExpiry
        )).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", refreshToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_MISMATCH));
        verify(authCookieUtil, never()).setCookie(any(), any(), any(), any());
    }
}
