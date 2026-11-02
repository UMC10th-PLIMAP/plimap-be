package com.example.plimap.domain.auth.service.command.impl;

import com.example.plimap.domain.auth.entity.OAuthMember;
import com.example.plimap.domain.auth.service.command.SocialAccountCommandService;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.global.config.OAuthProperties;
import com.example.plimap.global.security.AuthCookieUtil;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.OAuthFrontendRedirectCookieRepository;
import com.example.plimap.global.security.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthSuccessHandlerTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthCookieUtil authCookieUtil = new AuthCookieUtil();
    private final OAuth2AuthorizedClientRepository authorizedClientRepository =
            mock(OAuth2AuthorizedClientRepository.class);
    private final SocialAccountCommandService socialAccountCommandService = mock(SocialAccountCommandService.class);

    private OAuthFrontendRedirectCookieRepository redirectCookieRepository;
    private OAuthSuccessHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authCookieUtil, "cookieSecure", false);
        ReflectionTestUtils.setField(authCookieUtil, "cookieSameSite", "Lax");
        setUpHandler("http://localhost:5173/home");
    }

    @Test
    void 로그인_응답에서_지연_로딩된_CSRF_토큰을_강제로_로드한다() throws Exception {
        setUpTokenMocks();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CsrfToken csrfToken = mock(CsrfToken.class);
        request.setAttribute(CsrfToken.class.getName(), csrfToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication(true));

        verify(csrfToken).getToken();
        verify(refreshTokenService).save(1L, "refresh-token-jti", Duration.ofDays(14));
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.startsWith("accessToken=access-token-value"));
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.startsWith("refreshToken=refresh-token-value"));
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/home?isNewUser=false");
    }

    @Test
    void CSRF_토큰_속성이_없어도_예외없이_로그인_응답을_내려준다() throws Exception {
        setUpTokenMocks();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication(true)
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/home?isNewUser=false");
    }

    @Test
    void 온보딩을_완료하지_않은_사용자는_isNewUser_true로_리다이렉트한다() throws Exception {
        setUpTokenMocks();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication(false)
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/home?isNewUser=true");
    }

    @Test
    void 로컬_프론트에서_시작한_로그인은_로컬로_리다이렉트한다() throws Exception {
        setUpHandler("https://dev.plimap.kr/home");
        setUpTokenMocks();
        MockHttpServletRequest request = callbackRequestFor("http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication(true));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/home?isNewUser=false");
    }

    @Test
    void Dev_프론트에서_시작한_로그인은_Dev로_리다이렉트한다() throws Exception {
        setUpHandler("https://dev.plimap.kr/home");
        setUpTokenMocks();
        MockHttpServletRequest request = callbackRequestFor("https://dev.plimap.kr");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication(true));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://dev.plimap.kr/home?isNewUser=false");
    }

    @Test
    void 기본_리다이렉트_URI의_query를_보존한_채_isNewUser를_추가한다() throws Exception {
        setUpHandler("http://localhost:5173/home?foo=bar");
        setUpTokenMocks();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication(false)
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/home?foo=bar&isNewUser=true");
    }

    @Test
    void 웹_구글_로그인에서_받은_refresh_token을_저장하고_authorized_client를_지운다() throws Exception {
        setUpTokenMocks();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
        when(authentication.getPrincipal()).thenReturn(new OAuthMember(member, Collections.emptyMap()));
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(client.getRefreshToken()).thenReturn(new OAuth2RefreshToken("google-refresh-token", null));
        when(authorizedClientRepository.loadAuthorizedClient("google", authentication, request)).thenReturn(client);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(socialAccountCommandService).saveRefreshToken(1L, "google-refresh-token");
        verify(authorizedClientRepository).removeAuthorizedClient("google", authentication, request, response);
    }

    private void setUpHandler(String redirectUri) {
        OAuthProperties properties = new OAuthProperties(
                redirectUri,
                List.of("http://localhost:5173", "https://dev.plimap.kr")
        );
        redirectCookieRepository =
                new OAuthFrontendRedirectCookieRepository(authCookieUtil, properties);
        handler = new OAuthSuccessHandler(
                jwtUtil,
                refreshTokenService,
                authCookieUtil,
                redirectCookieRepository,
                authorizedClientRepository,
                socialAccountCommandService
        );
    }

    private void setUpTokenMocks() {
        when(jwtUtil.createAccessToken(any())).thenReturn("access-token-value");
        when(jwtUtil.createRefreshToken(any())).thenReturn("refresh-token-value");
        when(jwtUtil.getJti("refresh-token-value")).thenReturn("refresh-token-jti");
        when(jwtUtil.getAccessTokenExpiry()).thenReturn(Duration.ofDays(1));
        when(jwtUtil.getRefreshTokenExpiry()).thenReturn(Duration.ofDays(14));
    }

    private MockHttpServletRequest callbackRequestFor(String frontendOrigin) {
        MockHttpServletRequest authorizationRequest = new MockHttpServletRequest();
        authorizationRequest.addParameter("frontendOrigin", frontendOrigin);
        MockHttpServletResponse authorizationResponse = new MockHttpServletResponse();
        redirectCookieRepository.saveRequestedOrigin(
                authorizationRequest,
                authorizationResponse,
                "test-state"
        );
        Cookie originCookie = authorizationResponse.getCookie("oauth2_frontend_origin");
        assertThat(originCookie).isNotNull();

        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setCookies(originCookie);
        callbackRequest.addParameter("state", "test-state");
        return callbackRequest;
    }

    private Authentication authentication(boolean isOnboarded) {
        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(isOnboarded);
        when(member.getId()).thenReturn(1L);
        OAuthMember oAuthMember = new OAuthMember(member, Collections.emptyMap());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(oAuthMember);
        return authentication;
    }
}
