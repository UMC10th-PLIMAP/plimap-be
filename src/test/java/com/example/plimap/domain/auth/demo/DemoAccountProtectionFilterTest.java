package com.example.plimap.domain.auth.demo;

import com.example.plimap.domain.auth.config.DemoAuthProperties;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DemoAccountProtectionFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 컨텍스트_경로와_쿼리가_있어도_데모_탈퇴를_차단한다() throws Exception {
        // given
        authenticate(42L);
        var filter = new DemoAccountProtectionFilter(new DemoAuthProperties(false, 42L), new ObjectMapper());
        var request = new MockHttpServletRequest("DELETE", "/backend/api/v1/members/me");
        request.setContextPath("/backend");
        request.setQueryString("memberId=99");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTH_DEMO_ACCOUNT_WITHDRAWAL_NOT_ALLOWED");
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/members/me",
            "PATCH, /api/v1/members/me",
            "POST, /api/v1/members/me/profile-image",
            "DELETE, /api/v1/members/me/profile-image",
            "DELETE, /api/v1/auth/logout",
            "DELETE, /api/v1/members/99/follow"
    })
    void 탈퇴_외의_기능은_데모_계정도_통과한다(String method, String path) throws Exception {
        // given
        authenticate(42L);
        var filter = new DemoAccountProtectionFilter(new DemoAuthProperties(true, 42L), new ObjectMapper());
        var request = new MockHttpServletRequest(method, path);
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // when
        filter.doFilter(request, response, chain);

        // then
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 99})
    void 데모_ID가_미설정이거나_다른_회원이면_통과한다(Long demoId) throws Exception {
        // given
        authenticate(42L);
        var filter = new DemoAccountProtectionFilter(new DemoAuthProperties(true, demoId), new ObjectMapper());
        var request = new MockHttpServletRequest("DELETE", "/api/v1/members/me");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // when
        filter.doFilter(request, response, chain);

        // then
        verify(chain).doFilter(request, response);
    }

    private void authenticate(Long memberId) {
        Member member = Member.create(null, MemberRole.USER);
        ReflectionTestUtils.setField(member, "id", memberId);
        AuthMember principal = new AuthMember(member);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
