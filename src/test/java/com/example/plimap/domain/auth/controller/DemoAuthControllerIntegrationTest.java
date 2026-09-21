package com.example.plimap.domain.auth.controller;

import com.example.plimap.domain.auth.config.DemoAuthProperties;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberRole;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.support.PostgisContainerConfiguration;
import com.example.plimap.support.RedisContainerConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgisContainerConfiguration.class, RedisContainerConfiguration.class})
@Transactional
class DemoAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private DemoAuthProperties properties;

    private Member demo;
    private Cookie csrfCookie;
    private String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        demo = Member.create(null, MemberRole.USER);
        demo.completeOnboarding("테스트계정");
        demo = memberRepository.saveAndFlush(demo);
        when(properties.enabled()).thenReturn(true);
        when(properties.memberId()).thenReturn(demo.getId());

        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.result.token");
    }

    @Test
    void 익명_접속으로_24시간_쿠키를_받고_내_정보를_조회한다() throws Exception {
        // given, when
        MvcResult login = loginDemo();
        Cookie accessCookie = login.getResponse().getCookie("accessToken");

        // then
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(accessCookie.getPath()).isEqualTo("/");
        assertThat(accessCookie.getMaxAge()).isEqualTo(86400);
        var claims = jwtUtil.parseToken(accessCookie.getValue());
        assertThat(claims.getSubject()).isEqualTo(demo.getId().toString());
        assertThat(claims.get("tokenType")).isEqualTo("access");
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofDays(1));
        Cookie refreshCookie = login.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getMaxAge()).isZero();
        assertThat(redisTemplate.hasKey("refresh:token:" + demo.getId())).isFalse();
        mockMvc.perform(get("/api/v1/members/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(demo.getId()))
                .andExpect(jsonPath("$.result.nickname").value("테스트계정"));
    }

    @Test
    void 로그아웃_후_다시_버튼을_누르면_새_토큰으로_접속한다() throws Exception {
        // given
        Cookie first = loginDemo().getResponse().getCookie("accessToken");

        // when
        mockMvc.perform(delete("/api/v1/auth/logout")
                        .cookie(first, csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/members/me").cookie(first))
                .andExpect(status().isUnauthorized());
        Cookie second = loginDemo().getResponse().getCookie("accessToken");

        // then
        assertThat(jwtUtil.getJti(second.getValue())).isNotEqualTo(jwtUtil.getJti(first.getValue()));
        mockMvc.perform(get("/api/v1/members/me").cookie(second)).andExpect(status().isOk());
    }

    @Test
    void CSRF_헤더가_없거나_틀리면_접속하지_못한다() throws Exception {
        // given, when, then
        mockMvc.perform(post("/api/v1/auth/demo")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/demo").cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "incorrect-test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_회원_ID를_보내도_설정된_계정으로만_접속한다() throws Exception {
        // given, when
        MvcResult result = mockMvc.perform(post("/api/v1/auth/demo").param("memberId", "999999")
                        .cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk()).andReturn();

        // then
        assertThat(jwtUtil.getMemberId(result.getResponse().getCookie("accessToken").getValue()))
                .isEqualTo(demo.getId());
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    void 정지나_탈퇴한_계정은_접속하지_못한다(MemberStatus status) throws Exception {
        // given
        ReflectionTestUtils.setField(demo, "status", status);
        memberRepository.flush();

        // when, then
        assertRejected(404, "MEMBER_NOT_FOUND");
    }

    @Test
    void 삭제된_계정은_접속하지_못한다() throws Exception {
        // given
        demo.delete();
        memberRepository.flush();

        // when, then
        assertRejected(404, "MEMBER_NOT_FOUND");
    }

    @Test
    void 존재하지_않는_계정은_접속하지_못한다() throws Exception {
        // given
        when(properties.memberId()).thenReturn(Long.MAX_VALUE);

        // when, then
        assertRejected(404, "MEMBER_NOT_FOUND");
    }

    @Test
    void 관리자_계정은_접속하지_못한다() throws Exception {
        // given
        demo.grantAdmin();
        memberRepository.flush();

        // when, then
        assertRejected(503, "AUTH_DEMO_LOGIN_UNAVAILABLE");
    }

    @Test
    void 기능이_꺼져_있으면_접속하지_못한다() throws Exception {
        // given
        when(properties.enabled()).thenReturn(false);

        // when, then
        assertRejected(503, "AUTH_DEMO_LOGIN_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 데모_계정은_기능_활성화와_관계없이_쿠키로_탈퇴할_수_없다(boolean enabled) throws Exception {
        // given
        Cookie access = loginDemo().getResponse().getCookie("accessToken");
        when(properties.enabled()).thenReturn(enabled);

        // when, then
        mockMvc.perform(delete("/api/v1/members/me")
                        .cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_DEMO_ACCOUNT_WITHDRAWAL_NOT_ALLOWED"));
        assertThat(demo.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(demo.getDeletedAt()).isNull();
        mockMvc.perform(get("/api/v1/members/me").cookie(access)).andExpect(status().isOk());
    }

    @Test
    void 데모_계정은_Bearer_인증으로도_탈퇴할_수_없다() throws Exception {
        // given
        Cookie access = loginDemo().getResponse().getCookie("accessToken");

        // when, then
        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access.getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_DEMO_ACCOUNT_WITHDRAWAL_NOT_ALLOWED"));
        assertThat(demo.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void 일반_계정은_기존처럼_탈퇴할_수_있다() throws Exception {
        // given
        Member regular = Member.create(null, MemberRole.USER);
        regular.completeOnboarding("일반회원");
        regular = memberRepository.saveAndFlush(regular);
        String access = jwtUtil.createAccessToken(new AuthMember(regular));

        // when
        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());

        // then
        assertThat(regular.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(demo.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void 익명_탈퇴_요청은_기존처럼_인증이_필요하다() throws Exception {
        // given, when, then
        mockMvc.perform(delete("/api/v1/members/me")
                        .cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult loginDemo() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/demo")
                        .cookie(csrfCookie, new Cookie("refreshToken", "previous-test-cookie"))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DEMO_LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andReturn();
    }

    private void assertRejected(int status, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/demo")
                        .cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code)).andReturn();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.startsWith("accessToken="));
    }
}
