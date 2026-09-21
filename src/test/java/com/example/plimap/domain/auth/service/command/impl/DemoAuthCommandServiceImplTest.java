package com.example.plimap.domain.auth.service.command.impl;

import com.example.plimap.domain.auth.config.DemoAuthProperties;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberRole;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.member.service.query.MemberQueryService;
import com.example.plimap.global.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DemoAuthCommandServiceImplTest {

    private final MemberQueryService memberQueryService = mock(MemberQueryService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);

    @Test
    void 설정된_일반_회원의_액세스_토큰을_발급한다() {
        // given
        Member member = Member.create(null, MemberRole.USER);
        when(memberQueryService.getActiveMember(42L)).thenReturn(member);
        when(jwtUtil.createAccessToken(any(AuthMember.class))).thenReturn("test-only-access-token");
        var service = service(true, 42L);

        // when
        String token = service.issueAccessToken();

        // then
        assertThat(token).isEqualTo("test-only-access-token");
        verify(memberQueryService).getActiveMember(42L);
        verify(jwtUtil).createAccessToken(any(AuthMember.class));
    }

    @Test
    void 비활성화된_경우_회원_조회나_토큰_발급을_하지_않는다() {
        // given
        var service = service(false, 42L);

        // when, then
        assertUnavailable(service);
        verifyNoInteractions(memberQueryService, jwtUtil);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void 회원_ID가_없거나_잘못되면_발급하지_않는다(Long memberId) {
        // given
        var service = service(true, memberId);

        // when, then
        assertUnavailable(service);
        verifyNoInteractions(memberQueryService, jwtUtil);
    }

    @Test
    void 관리자로_설정된_계정의_토큰은_발급하지_않는다() {
        // given
        when(memberQueryService.getActiveMember(42L)).thenReturn(Member.create(null, MemberRole.ADMIN));
        var service = service(true, 42L);

        // when, then
        assertUnavailable(service);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void 활성_회원_조회에_실패하면_토큰을_발급하지_않는다() {
        // given
        when(memberQueryService.getActiveMember(42L))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        var service = service(true, 42L);

        // when, then
        assertThatThrownBy(service::issueAccessToken)
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(jwtUtil);
    }

    private DemoAuthCommandServiceImpl service(boolean enabled, Long memberId) {
        return new DemoAuthCommandServiceImpl(new DemoAuthProperties(enabled, memberId), memberQueryService, jwtUtil);
    }

    private void assertUnavailable(DemoAuthCommandServiceImpl service) {
        assertThatThrownBy(service::issueAccessToken)
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.DEMO_LOGIN_UNAVAILABLE));
    }
}
