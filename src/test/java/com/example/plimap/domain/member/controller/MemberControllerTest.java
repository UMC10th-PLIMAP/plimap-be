package com.example.plimap.domain.member.controller;

import com.example.plimap.domain.auth.service.command.impl.CustomOAuthService;
import com.example.plimap.domain.auth.service.command.impl.OAuthFailureHandler;
import com.example.plimap.domain.auth.service.command.impl.OAuthSuccessHandler;
import com.example.plimap.domain.member.dto.Pagination;
import com.example.plimap.domain.member.dto.response.MemberResponse;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.enums.SuspensionPeriod;
import com.example.plimap.domain.member.enums.WithdrawalReason;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.member.service.command.MemberCommandService;
import com.example.plimap.domain.member.service.query.MemberQueryService;
import com.example.plimap.domain.report.enums.ReportCategory;
import java.time.Instant;
import com.example.plimap.global.apiPayload.code.GeneralErrorCode;
import com.example.plimap.global.apiPayload.exception.GlobalExceptionHandler;
import com.example.plimap.global.config.CorsConfig;
import com.example.plimap.global.config.SecurityConfig;
import com.example.plimap.global.security.AuthCookieUtil;
import com.example.plimap.global.security.HttpCookieOAuth2AuthorizationRequestRepository;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.RefreshTokenService;
import com.example.plimap.global.security.SecurityErrorResponseHandler;
import com.example.plimap.global.security.SessionInvalidationService;
import com.example.plimap.global.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemberController.class)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        SecurityErrorResponseHandler.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class MemberControllerTest {

    private static final String ACCESS_TOKEN = "valid-access-token";
    private static final Long AUTH_MEMBER_ID = 1L;
    private static final Long TARGET_MEMBER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomOAuthService customOAuthService;

    @MockitoBean
    private OAuthSuccessHandler oAuthSuccessHandler;

    @MockitoBean
    private OAuthFailureHandler oAuthFailureHandler;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private AuthCookieUtil authCookieUtil;

    @MockitoBean
    private HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    @MockitoBean
    private MemberCommandService memberCommandService;

    @MockitoBean
    private MemberQueryService memberQueryService;

    @MockitoBean
    private SessionInvalidationService sessionInvalidationService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.isValid(ACCESS_TOKEN)).thenReturn(true);
        when(jwtUtil.isAccessToken(ACCESS_TOKEN)).thenReturn(true);
        when(jwtUtil.getJti(ACCESS_TOKEN)).thenReturn("test-jti");
        when(tokenBlacklistService.isBlacklisted("test-jti")).thenReturn(false);
        when(jwtUtil.getMemberId(ACCESS_TOKEN)).thenReturn(AUTH_MEMBER_ID);

        Member authenticatedMember = Member.builder().build();
        ReflectionTestUtils.setField(authenticatedMember, "id", AUTH_MEMBER_ID);
        when(memberRepository.findById(AUTH_MEMBER_ID))
                .thenReturn(Optional.of(authenticatedMember));
    }

    @Test
    void 내_프로필_조회에_성공하면_200과_MY_PROFILE_FETCHED_응답을_반환한다() throws Exception {
        MemberResponse.MyProfile profile = new MemberResponse.MyProfile(
                AUTH_MEMBER_ID, "예림", "이예림", "소개", "key", 3L, 5L, Instant.parse("2026-01-01T00:00:00Z"), 7L,
                MemberStatus.ACTIVE, null, null, null, null, 0, null);
        when(memberQueryService.getMyProfile(AUTH_MEMBER_ID)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_MY_PROFILE_FETCHED_SUCCESS"))
                .andExpect(jsonPath("$.result.nickname").value("예림"))
                .andExpect(jsonPath("$.result.followerCount").value(3))
                .andExpect(jsonPath("$.result.followingCount").value(5))
                .andExpect(jsonPath("$.result.pinCount").value(7))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.penaltyPoint").value(0))
                .andExpect(jsonPath("$.result.lastPenaltyPeriod").value(nullValue()));
    }

    @Test
    void 정지_중인_회원의_내_프로필_조회는_제재_정보를_함께_반환한다() throws Exception {
        // MemberStatusInterceptor(CorsConfig에 등록)가 실제로 SUSPENDED 회원의
        // GET /api/v1/members/me를 허용 목록으로 통과시키는지까지 검증하기 위해,
        // 인증 principal을 만드는 memberRepository.findById()도 SUSPENDED로 재정의한다.
        Member suspendedMember = Member.builder().build();
        ReflectionTestUtils.setField(suspendedMember, "id", AUTH_MEMBER_ID);
        ReflectionTestUtils.setField(suspendedMember, "status", MemberStatus.SUSPENDED);
        ReflectionTestUtils.setField(suspendedMember, "suspendedUntil", Instant.parse("2026-08-20T00:00:00Z"));
        when(memberRepository.findById(AUTH_MEMBER_ID)).thenReturn(Optional.of(suspendedMember));

        MemberResponse.MyProfile profile = new MemberResponse.MyProfile(
                AUTH_MEMBER_ID, "예림", "이예림", "소개", "key", 3L, 5L, Instant.parse("2026-01-01T00:00:00Z"), 7L,
                MemberStatus.SUSPENDED, Instant.parse("2026-08-20T00:00:00Z"), null,
                ReportCategory.ABUSE_OR_HATE_SPEECH, "욕설 반복 신고 누적", 2, SuspensionPeriod.THREE_DAYS);
        when(memberQueryService.getMyProfile(AUTH_MEMBER_ID)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.result.suspendedUntil").value("2026-08-20T00:00:00Z"))
                .andExpect(jsonPath("$.result.reasonCategory").value("ABUSE_OR_HATE_SPEECH"))
                .andExpect(jsonPath("$.result.reasonDetail").value("욕설 반복 신고 누적"))
                .andExpect(jsonPath("$.result.penaltyPoint").value(2))
                .andExpect(jsonPath("$.result.lastPenaltyPeriod").value("THREE_DAYS"));
    }

    @Test
    void 벌점으로_탈퇴된_회원의_내_프로필_조회는_탈퇴_사유를_함께_반환한다() throws Exception {
        // MemberStatusInterceptor가 WITHDRAWN 회원의 GET /api/v1/members/me도
        // 허용 목록으로 통과시키는지까지 검증한다.
        Member withdrawnMember = Member.builder().build();
        ReflectionTestUtils.setField(withdrawnMember, "id", AUTH_MEMBER_ID);
        ReflectionTestUtils.setField(withdrawnMember, "status", MemberStatus.WITHDRAWN);
        when(memberRepository.findById(AUTH_MEMBER_ID)).thenReturn(Optional.of(withdrawnMember));

        MemberResponse.MyProfile profile = new MemberResponse.MyProfile(
                AUTH_MEMBER_ID, "플리맵사용자", null, null, null, 0L, 0L, Instant.parse("2026-01-01T00:00:00Z"), 0L,
                MemberStatus.WITHDRAWN, null, WithdrawalReason.PENALTY,
                ReportCategory.OBSCENE_OR_HARMFUL, "음란물 반복 게시", 4, SuspensionPeriod.PERMANENT);
        when(memberQueryService.getMyProfile(AUTH_MEMBER_ID)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.result.withdrawalReason").value("PENALTY"))
                .andExpect(jsonPath("$.result.reasonCategory").value("OBSCENE_OR_HARMFUL"))
                .andExpect(jsonPath("$.result.reasonDetail").value("음란물 반복 게시"))
                .andExpect(jsonPath("$.result.penaltyPoint").value(4))
                .andExpect(jsonPath("$.result.lastPenaltyPeriod").value("PERMANENT"));
    }

    @Test
    void 다른_사용자_프로필_조회에_성공하면_200과_OTHER_PROFILE_FETCHED_응답을_반환한다() throws Exception {
        MemberResponse.OtherProfile profile = new MemberResponse.OtherProfile(
                TARGET_MEMBER_ID, "상대방", "김상대", "소개", "key", 3L, 5L, true, false, 7L);
        when(memberQueryService.getOtherProfile(AUTH_MEMBER_ID, TARGET_MEMBER_ID)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/members/{memberId}", TARGET_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_OTHER_PROFILE_FETCHED_SUCCESS"))
                .andExpect(jsonPath("$.result.nickname").value("상대방"))
                .andExpect(jsonPath("$.result.isFollowing").value(true))
                .andExpect(jsonPath("$.result.isFollowingViewer").value(false))
                .andExpect(jsonPath("$.result.pinCount").value(7));
    }

    @Test
    void 존재하지_않는_회원의_프로필을_조회하면_404를_반환한다() throws Exception {
        when(memberQueryService.getOtherProfile(AUTH_MEMBER_ID, TARGET_MEMBER_ID))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/members/{memberId}", TARGET_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 본인의_memberId로_다른_사용자_프로필_조회를_요청하면_400을_반환한다() throws Exception {
        when(memberQueryService.getOtherProfile(AUTH_MEMBER_ID, AUTH_MEMBER_ID))
                .thenThrow(new MemberException(MemberErrorCode.CANNOT_VIEW_SELF_PROFILE));

        mockMvc.perform(get("/api/v1/members/{memberId}", AUTH_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_CANNOT_VIEW_SELF_PROFILE"));
    }

    @Test
    void 언팔로우에_성공하면_200과_UNFOLLOWED_응답을_반환한다() throws Exception {
        doNothing().when(memberCommandService).unfollow(AUTH_MEMBER_ID, TARGET_MEMBER_ID);

        mockMvc.perform(delete("/api/v1/members/{memberId}/follow", TARGET_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_UNFOLLOWED_SUCCESS"))
                .andExpect(jsonPath("$.message").value("언팔로우했습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(memberCommandService).unfollow(AUTH_MEMBER_ID, TARGET_MEMBER_ID);
    }

    @Test
    void 팔로우_중이_아닌_회원을_언팔로우하면_404를_반환한다() throws Exception {
        doThrow(new MemberException(MemberErrorCode.NOT_FOLLOWING))
                .when(memberCommandService).unfollow(AUTH_MEMBER_ID, TARGET_MEMBER_ID);

        mockMvc.perform(delete("/api/v1/members/{memberId}/follow", TARGET_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOLLOWING"))
                .andExpect(jsonPath("$.message").value("팔로우 중이 아닌 사용자입니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 회원_탈퇴에_성공하면_200과_WITHDRAWN_응답을_반환하고_세션을_무효화한다() throws Exception {
        doNothing().when(memberCommandService).withdraw(AUTH_MEMBER_ID, null);

        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_WITHDRAWN_SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(memberCommandService).withdraw(AUTH_MEMBER_ID, null);
        verify(sessionInvalidationService).invalidate(any(), any());
    }

    @Test
    void 탈퇴_후_다른_기기의_액세스_토큰은_인증이_거부되고_재탈퇴도_거부된다() throws Exception {
        String otherDeviceToken = "other-device-access-token";
        String otherDeviceJti = "other-device-jti";
        when(jwtUtil.isValid(otherDeviceToken)).thenReturn(true);
        when(jwtUtil.isAccessToken(otherDeviceToken)).thenReturn(true);
        when(jwtUtil.getJti(otherDeviceToken)).thenReturn(otherDeviceJti);
        when(jwtUtil.getMemberId(otherDeviceToken)).thenReturn(AUTH_MEMBER_ID);
        when(tokenBlacklistService.isBlacklisted(otherDeviceJti)).thenReturn(false);

        doNothing().when(memberCommandService).withdraw(AUTH_MEMBER_ID, null);

        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        // 탈퇴 반영: 회원의 status/deletedAt이 더 이상 ACTIVE 조건을 만족하지 않는 DB 상태를 시뮬레이션한다.
        when(memberRepository.findById(AUTH_MEMBER_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherDeviceToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherDeviceToken))
                .andExpect(status().isUnauthorized());

        verify(memberCommandService).withdraw(AUTH_MEMBER_ID, null);
    }

    @Test
    void 팔로워_목록_조회에_성공하면_200과_FOLLOWERS_FETCHED_응답을_반환한다() throws Exception {
        MemberResponse.FollowerItem follower = new MemberResponse.FollowerItem(
                3L, "팔로워", "이름", "key", Instant.parse("2026-01-01T00:00:00Z"), true, false);
        Pagination<MemberResponse.FollowerItem> page = Pagination.<MemberResponse.FollowerItem>builder()
                .data(List.of(follower))
                .nextCursor(null)
                .hasNext(false)
                .pageSize(10)
                .build();
        when(memberQueryService.findFollowers(eq(AUTH_MEMBER_ID), eq(TARGET_MEMBER_ID), isNull(), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/{memberId}/followers", TARGET_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_FOLLOWERS_FETCHED_SUCCESS"))
                .andExpect(jsonPath("$.result.data[0].nickname").value("팔로워"))
                .andExpect(jsonPath("$.result.data[0].isFollowing").value(true))
                .andExpect(jsonPath("$.result.data[0].isFollowingViewer").value(false))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void 존재하지_않는_회원의_팔로워_목록을_조회하면_404를_반환한다() throws Exception {
        when(memberQueryService.findFollowers(eq(AUTH_MEMBER_ID), eq(TARGET_MEMBER_ID), isNull(), eq(10)))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/members/{memberId}/followers", TARGET_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 팔로워_목록_조회시_pageSize가_50을_초과하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/{memberId}/followers", TARGET_MEMBER_ID)
                        .param("pageSize", "51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 팔로워_목록_조회시_pageSize가_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/{memberId}/followers", TARGET_MEMBER_ID)
                        .param("pageSize", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 팔로워_목록_조회시_pageSize가_음수면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/{memberId}/followers", TARGET_MEMBER_ID)
                        .param("pageSize", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 팔로워_목록_조회시_pageSize가_50이면_허용된다() throws Exception {
        Pagination<MemberResponse.FollowerItem> page = Pagination.<MemberResponse.FollowerItem>builder()
                .data(List.of())
                .nextCursor(null)
                .hasNext(false)
                .pageSize(50)
                .build();
        when(memberQueryService.findFollowers(eq(AUTH_MEMBER_ID), eq(TARGET_MEMBER_ID), isNull(), eq(50))).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/{memberId}/followers", TARGET_MEMBER_ID)
                        .param("pageSize", "50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(memberQueryService).findFollowers(AUTH_MEMBER_ID, TARGET_MEMBER_ID, null, 50);
    }

    @Test
    void 팔로워_목록_조회시_cursor를_전달하면_그대로_서비스에_전달된다() throws Exception {
        String cursor = "2026-01-01T00:00:00Z/3";
        Pagination<MemberResponse.FollowerItem> page = Pagination.<MemberResponse.FollowerItem>builder()
                .data(List.of())
                .nextCursor(null)
                .hasNext(false)
                .pageSize(10)
                .build();
        when(memberQueryService.findFollowers(AUTH_MEMBER_ID, TARGET_MEMBER_ID, cursor, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/{memberId}/followers", TARGET_MEMBER_ID)
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(memberQueryService).findFollowers(AUTH_MEMBER_ID, TARGET_MEMBER_ID, cursor, 10);
    }

    @Test
    void 팔로워_목록_조회시_잘못된_커서면_400을_반환한다() throws Exception {
        when(memberQueryService.findFollowers(eq(AUTH_MEMBER_ID), eq(TARGET_MEMBER_ID), eq("invalid-cursor"), eq(10)))
                .thenThrow(new MemberException(GeneralErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/members/{memberId}/followers", TARGET_MEMBER_ID)
                        .param("cursor", "invalid-cursor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_INVALID_CURSOR"));
    }

    @Test
    void 팔로잉_목록_조회에_성공하면_200과_FOLLOWING_FETCHED_응답을_반환한다() throws Exception {
        MemberResponse.FollowingItem following = new MemberResponse.FollowingItem(
                3L, "팔로잉", "이름", "key", Instant.parse("2026-01-01T00:00:00Z"), true, false);
        Pagination<MemberResponse.FollowingItem> page = Pagination.<MemberResponse.FollowingItem>builder()
                .data(List.of(following))
                .nextCursor(null)
                .hasNext(false)
                .pageSize(10)
                .build();
        when(memberQueryService.findFollowing(eq(AUTH_MEMBER_ID), eq(TARGET_MEMBER_ID), isNull(), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/{memberId}/following", TARGET_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_FOLLOWING_FETCHED_SUCCESS"))
                .andExpect(jsonPath("$.result.data[0].nickname").value("팔로잉"))
                .andExpect(jsonPath("$.result.data[0].isFollowing").value(true))
                .andExpect(jsonPath("$.result.data[0].isFollowingViewer").value(false))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void 존재하지_않는_회원의_팔로잉_목록을_조회하면_404를_반환한다() throws Exception {
        when(memberQueryService.findFollowing(eq(AUTH_MEMBER_ID), eq(TARGET_MEMBER_ID), isNull(), eq(10)))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/members/{memberId}/following", TARGET_MEMBER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 팔로잉_목록_조회시_pageSize가_50을_초과하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/{memberId}/following", TARGET_MEMBER_ID)
                        .param("pageSize", "51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 팔로잉_목록_조회시_pageSize가_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/{memberId}/following", TARGET_MEMBER_ID)
                        .param("pageSize", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 팔로잉_목록_조회시_pageSize가_음수면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/{memberId}/following", TARGET_MEMBER_ID)
                        .param("pageSize", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 팔로잉_목록_조회시_pageSize가_50이면_허용된다() throws Exception {
        Pagination<MemberResponse.FollowingItem> page = Pagination.<MemberResponse.FollowingItem>builder()
                .data(List.of())
                .nextCursor(null)
                .hasNext(false)
                .pageSize(50)
                .build();
        when(memberQueryService.findFollowing(eq(AUTH_MEMBER_ID), eq(TARGET_MEMBER_ID), isNull(), eq(50))).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/{memberId}/following", TARGET_MEMBER_ID)
                        .param("pageSize", "50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(memberQueryService).findFollowing(AUTH_MEMBER_ID, TARGET_MEMBER_ID, null, 50);
    }

    @Test
    void 팔로잉_목록_조회시_cursor를_전달하면_그대로_서비스에_전달된다() throws Exception {
        String cursor = "2026-01-01T00:00:00Z/3";
        Pagination<MemberResponse.FollowingItem> page = Pagination.<MemberResponse.FollowingItem>builder()
                .data(List.of())
                .nextCursor(null)
                .hasNext(false)
                .pageSize(10)
                .build();
        when(memberQueryService.findFollowing(AUTH_MEMBER_ID, TARGET_MEMBER_ID, cursor, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/{memberId}/following", TARGET_MEMBER_ID)
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(memberQueryService).findFollowing(AUTH_MEMBER_ID, TARGET_MEMBER_ID, cursor, 10);
    }

    @Test
    void 팔로잉_목록_조회시_잘못된_커서면_400을_반환한다() throws Exception {
        when(memberQueryService.findFollowing(eq(AUTH_MEMBER_ID), eq(TARGET_MEMBER_ID), eq("invalid-cursor"), eq(10)))
                .thenThrow(new MemberException(GeneralErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/members/{memberId}/following", TARGET_MEMBER_ID)
                        .param("cursor", "invalid-cursor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_INVALID_CURSOR"));
    }

    @Test
    void 회원_검색에_성공하면_200과_MEMBERS_SEARCHED_응답을_반환한다() throws Exception {
        MemberResponse.SearchItem item = new MemberResponse.SearchItem(
                3L, "검색결과", "이름", "key", false, false, Instant.parse("2026-01-01T00:00:00Z"));
        Pagination<MemberResponse.SearchItem> page = Pagination.<MemberResponse.SearchItem>builder()
                .data(List.of(item))
                .nextCursor(null)
                .hasNext(false)
                .pageSize(10)
                .build();
        when(memberQueryService.searchActiveMembers(eq(AUTH_MEMBER_ID), eq("키워드"), isNull(), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/search")
                        .param("keyword", "키워드")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_MEMBERS_SEARCHED_SUCCESS"))
                .andExpect(jsonPath("$.result.data[0].nickname").value("검색결과"))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void 회원_검색시_keyword가_없어도_기본값으로_동작한다() throws Exception {
        Pagination<MemberResponse.SearchItem> page = Pagination.<MemberResponse.SearchItem>builder()
                .data(List.of())
                .nextCursor(null)
                .hasNext(false)
                .pageSize(10)
                .build();
        when(memberQueryService.searchActiveMembers(eq(AUTH_MEMBER_ID), eq(""), isNull(), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(memberQueryService).searchActiveMembers(AUTH_MEMBER_ID, "", null, 10);
    }

    @Test
    void 회원_검색시_pageSize가_50을_초과하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/search")
                        .param("pageSize", "51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회원_검색시_pageSize가_int_최댓값이면_400을_반환한다() throws Exception {
        // CodeRabbit 리뷰 검증용: MemberControllerDocs에만 있는 @Max(50)이 구현 메서드에도
        // 실제로 적용되는지(Spring이 인터페이스 파라미터 애노테이션을 인식하는지) 확인한다.
        mockMvc.perform(get("/api/v1/members/search")
                        .param("pageSize", "2147483647")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회원_검색시_pageSize가_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/search")
                        .param("pageSize", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회원_검색시_pageSize가_음수면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/search")
                        .param("pageSize", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회원_검색시_pageSize가_50이면_허용된다() throws Exception {
        Pagination<MemberResponse.SearchItem> page = Pagination.<MemberResponse.SearchItem>builder()
                .data(List.of())
                .nextCursor(null)
                .hasNext(false)
                .pageSize(50)
                .build();
        when(memberQueryService.searchActiveMembers(eq(AUTH_MEMBER_ID), eq(""), isNull(), eq(50))).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/search")
                        .param("pageSize", "50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(memberQueryService).searchActiveMembers(AUTH_MEMBER_ID, "", null, 50);
    }

    @Test
    void 회원_검색시_cursor를_전달하면_그대로_서비스에_전달된다() throws Exception {
        String cursor = "0/2/0/2026-01-01T00:00:00Z/3";
        Pagination<MemberResponse.SearchItem> page = Pagination.<MemberResponse.SearchItem>builder()
                .data(List.of())
                .nextCursor(null)
                .hasNext(false)
                .pageSize(10)
                .build();
        when(memberQueryService.searchActiveMembers(AUTH_MEMBER_ID, "키워드", cursor, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/search")
                        .param("keyword", "키워드")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(memberQueryService).searchActiveMembers(AUTH_MEMBER_ID, "키워드", cursor, 10);
    }

    @Test
    void 회원_검색시_잘못된_커서면_400을_반환한다() throws Exception {
        when(memberQueryService.searchActiveMembers(eq(AUTH_MEMBER_ID), eq(""), eq("invalid-cursor"), eq(10)))
                .thenThrow(new MemberException(GeneralErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/members/search")
                        .param("cursor", "invalid-cursor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_INVALID_CURSOR"));
    }

    @Test
    void 프로필_이미지_업로드에_성공하면_200과_PROFILE_IMAGE_UPLOADED_응답을_반환한다() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.webp", "image/webp", "webp-content".getBytes());
        MemberResponse.ProfileImage result = new MemberResponse.ProfileImage(
                "members/1/new.webp",
                "https://project.supabase.co/storage/v1/object/public/profile-images/members/1/new.webp"
        );
        when(memberCommandService.uploadProfileImage(eq(AUTH_MEMBER_ID), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/members/me/profile-image")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_IMAGE_UPLOADED_SUCCESS"))
                .andExpect(jsonPath("$.result.objectKey").value("members/1/new.webp"))
                .andExpect(jsonPath("$.result.imageUrl").value(result.imageUrl()));
    }

    @Test
    void Bearer_토큰과_CSRF_토큰_없이_프로필_이미지를_업로드하면_CSRF_검증에서_403으로_차단된다() throws Exception {
        // Bearer 인증 요청만 CSRF 검증에서 제외되므로(SecurityConfig 참고), Bearer 토큰이 없는
        // 상태 변경 요청은 인증 여부와 무관하게 CSRF 필터에서 먼저 403으로 막힌다.
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.webp", "image/webp", "webp-content".getBytes());

        mockMvc.perform(multipart("/api/v1/members/me/profile-image").file(image))
                .andExpect(status().isForbidden());
    }

    @Test
    void image_파트_없이_프로필_이미지_업로드를_요청하면_400을_반환한다() throws Exception {
        mockMvc.perform(multipart("/api/v1/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_MISSING_PARAMETER"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 유효하지_않은_프로필_이미지면_400을_반환한다() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.png", "image/png", "not-webp".getBytes());
        doThrow(new MemberException(MemberErrorCode.INVALID_PROFILE_IMAGE))
                .when(memberCommandService).uploadProfileImage(eq(AUTH_MEMBER_ID), any());

        mockMvc.perform(multipart("/api/v1/members/me/profile-image")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_PROFILE_IMAGE"));
    }

    @Test
    void 프로필_이미지_제거에_성공하면_200과_PROFILE_IMAGE_REMOVED_응답을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_IMAGE_REMOVED_SUCCESS"));

        verify(memberCommandService).removeProfileImage(AUTH_MEMBER_ID);
    }

    @Test
    void 이미_프로필_이미지가_없는_상태에서_제거를_요청하면_404를_반환한다() throws Exception {
        doThrow(new MemberException(MemberErrorCode.PROFILE_IMAGE_NOT_FOUND))
                .when(memberCommandService).removeProfileImage(AUTH_MEMBER_ID);

        mockMvc.perform(delete("/api/v1/members/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_IMAGE_NOT_FOUND"));
    }
}
