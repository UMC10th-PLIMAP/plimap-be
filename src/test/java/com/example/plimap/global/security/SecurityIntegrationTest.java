package com.example.plimap.global.security;

import com.example.plimap.domain.auth.controller.AuthController;
import com.example.plimap.domain.auth.controller.DemoAuthController;
import com.example.plimap.domain.auth.service.command.DemoAuthCommandService;
import java.time.Duration;
import com.example.plimap.domain.auth.service.command.impl.CustomOAuthService;
import com.example.plimap.domain.auth.service.command.impl.OAuthFailureHandler;
import com.example.plimap.domain.auth.service.command.impl.OAuthSuccessHandler;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberRole;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.member.service.command.MemberCommandService;
import com.example.plimap.domain.member.service.command.TermsCommandService;
import com.example.plimap.domain.member.service.query.TermsQueryService;
import com.example.plimap.global.config.CorsConfig;
import com.example.plimap.global.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        SecurityIntegrationTest.TestController.class,
        SecurityIntegrationTest.AdminTestController.class,
        AuthController.class,
        DemoAuthController.class
})
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        SecurityErrorResponseHandler.class,
        AuthCookieUtil.class,
        HttpCookieOAuth2AuthorizationRequestRepository.class,
        SecurityIntegrationTest.TestController.class,
        SecurityIntegrationTest.AdminTestController.class
})
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DEV_ORIGIN = "https://dev.plimap.kr";
    private static final String PREVIEW_ORIGIN = "https://pr-123.plimap.kr";
    private static final String PRIVATE_NETWORK_ORIGIN = "http://192.168.1.10:5173";
    private static final String PRIVATE_NETWORK_LOOKALIKE_ORIGIN = "http://192.168.1.10.evil:5173";
    private static final String PROTECTED_PATH = "/api/v1/security-test";
    private static final String ADMIN_PATH = "/api/v1/admin/security-test";
    private static final String ACCESS_TOKEN = "valid-access-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomOAuthService customOAuthService;

    @MockitoBean
    private OAuthSuccessHandler oAuthSuccessHandler;

    @MockitoBean
    private OAuthFailureHandler oAuthFailureHandler;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private MemberCommandService memberCommandService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private TermsQueryService termsQueryService;

    @MockitoBean
    private TermsCommandService termsCommandService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private SessionInvalidationService sessionInvalidationService;

    @MockitoBean
    private DemoAuthCommandService demoAuthCommandService;

    @BeforeEach
    void setUp() {
        Member member = Member.builder().build();
        when(jwtUtil.isValid(ACCESS_TOKEN)).thenReturn(true);
        when(jwtUtil.isAccessToken(ACCESS_TOKEN)).thenReturn(true);
        when(jwtUtil.getMemberId(ACCESS_TOKEN)).thenReturn(1L);
        when(jwtUtil.getJti(ACCESS_TOKEN)).thenReturn("test-jti");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(tokenBlacklistService.isBlacklisted("test-jti")).thenReturn(false);
    }

    @Test
    void 데모_로그인은_CSRF_확인_후_익명_요청에_인증_쿠키를_발급한다() throws Exception {
        // given
        when(demoAuthCommandService.issueAccessToken()).thenReturn(ACCESS_TOKEN);
        when(jwtUtil.getAccessTokenExpiry()).thenReturn(Duration.ofDays(1));
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");

        // when
        MvcResult login = mockMvc.perform(post("/api/v1/auth/demo")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DEMO_LOGIN_SUCCESS"))
                .andReturn();

        // then
        assertThat(login.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(value -> assertThat(value).startsWith("accessToken=")
                        .contains("HttpOnly", "Path=/", "Max-Age=86400", "SameSite=Lax"))
                .anySatisfy(value -> assertThat(value).startsWith("refreshToken=;").contains("Max-Age=0"));
        org.mockito.Mockito.verifyNoInteractions(refreshTokenService);
    }

    @Test
    void 데모_로그인도_CSRF_헤더_없이_호출하면_거부한다() throws Exception {
        // given, when, then
        mockMvc.perform(post("/api/v1/auth/demo")).andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(demoAuthCommandService);
    }

    @Test
    void 미인증_요청은_공통_401_응답을_반환한다() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"));
    }

    @Test
    void OAuth와_헬스체크와_Swagger_OpenAPI_경로는_인증_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/oauth/authorization/kakao"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void 허용된_로컬_Origin으로_OAuth_로그인을_시작하면_Origin_쿠키를_발급한다() throws Exception {
        mockMvc.perform(get("/oauth/authorization/google")
                        .param("frontendOrigin", ALLOWED_ORIGIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .anyMatch(header -> header.startsWith("oauth2_frontend_origin=")));
    }

    @Test
    void 허용된_Dev_Origin으로_OAuth_로그인을_시작할_수_있다() throws Exception {
        mockMvc.perform(get("/oauth/authorization/google")
                        .param("frontendOrigin", DEV_ORIGIN))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void 허용되지_않은_Origin으로_OAuth_로그인을_시작하면_거부한다() throws Exception {
        mockMvc.perform(get("/oauth/authorization/google")
                        .param("frontendOrigin", "https://attacker.example"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 최초_GET_응답에서_읽을_수_있는_CSRF_쿠키를_발급한다() throws Exception {
        MvcResult result = mockMvc.perform(get(PROTECTED_PATH)
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"))
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();
        assertThat(csrfCookie.getSecure()).isFalse();
        assertThat(csrfCookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(csrfCookie.getPath()).isEqualTo("/");
    }

    @Test
    void 정지_회원은_CSRF_토큰을_발급받아_로그아웃할_수_있다() throws Exception {
        Member suspended = Member.builder().status(MemberStatus.SUSPENDED).build();
        ReflectionTestUtils.setField(suspended, "suspendedUntil", Instant.now().plusSeconds(3600));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(suspended));

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        mockMvc.perform(delete("/api/v1/auth/logout")
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk());

        verify(sessionInvalidationService).invalidate(any(), any());
    }

    @Test
    void 쿠키_인증_POST는_CSRF_토큰이_없으면_차단한다() throws Exception {
        mockMvc.perform(post(PROTECTED_PATH)
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_403_FORBIDDEN"));
    }

    @Test
    void 쿠키와_헤더의_CSRF_토큰이_일치하면_POST가_CSRF_검사를_통과한다() throws Exception {
        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(post(PROTECTED_PATH)
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void Bearer_인증_POST는_CSRF_토큰_없이_CSRF_검사를_통과한다() throws Exception {
        mockMvc.perform(post(PROTECTED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void 유효하지_않은_Bearer가_있으면_쿠키_인증으로_fallback하지_않는다() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"));
    }

    @Test
    void 허용된_Origin의_credential_Preflight를_처리한다() throws Exception {
        mockMvc.perform(options(PROTECTED_PATH)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, Content-Type, X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("X-XSRF-TOKEN")
                ));
    }

    @Test
    void Dev_Origin의_credential_Preflight를_처리한다() throws Exception {
        mockMvc.perform(options(PROTECTED_PATH)
                        .header(HttpHeaders.ORIGIN, DEV_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 사설망_Origin의_credential_Preflight를_처리한다() throws Exception {
        mockMvc.perform(options(PROTECTED_PATH)
                        .header(HttpHeaders.ORIGIN, PRIVATE_NETWORK_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PRIVATE_NETWORK_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void previewOriginCredentialPreflightIsProcessed() throws Exception {
        mockMvc.perform(options(PROTECTED_PATH)
                        .header(HttpHeaders.ORIGIN, PREVIEW_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PREVIEW_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 관리자_전용_경로는_일반_회원이면_거부한다() throws Exception {
        mockMvc.perform(get(ADMIN_PATH)
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자_전용_경로는_관리자_회원이면_허용한다() throws Exception {
        Member admin = Member.builder().role(MemberRole.ADMIN).build();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));

        mockMvc.perform(get(ADMIN_PATH)
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    @Test
    void 허용되지_않은_Origin의_Preflight를_차단한다() throws Exception {
        mockMvc.perform(options(PROTECTED_PATH)
                        .header(HttpHeaders.ORIGIN, "https://attacker.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void 사설망_유사_호스트의_Preflight를_차단한다() throws Exception {
        mockMvc.perform(options(PROTECTED_PATH)
                        .header(HttpHeaders.ORIGIN, PRIVATE_NETWORK_LOOKALIKE_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void 허용된_Preview_Origin으로_OAuth_로그인을_시작할_수_있다() throws Exception {
        mockMvc.perform(get("/oauth/authorization/google")
                        .param("frontendOrigin", PREVIEW_ORIGIN))
                .andExpect(status().is3xxRedirection());
    }

    private Cookie issueCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get(PROTECTED_PATH)
                        .cookie(new Cookie("accessToken", ACCESS_TOKEN)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    @RestController
    @RequestMapping(PROTECTED_PATH)
    public static class TestController {

        @GetMapping
        public ResponseEntity<String> get() {
            return ResponseEntity.ok("ok");
        }

        @PostMapping
        public ResponseEntity<String> post() {
            return ResponseEntity.ok("ok");
        }
    }

    @RestController
    @RequestMapping("/api/v1/admin/security-test")
    public static class AdminTestController {

        @GetMapping
        public ResponseEntity<String> get() {
            return ResponseEntity.ok("admin-ok");
        }
    }
}
