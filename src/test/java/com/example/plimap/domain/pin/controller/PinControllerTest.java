package com.example.plimap.domain.pin.controller;

import com.example.plimap.domain.auth.service.command.impl.CustomOAuthService;
import com.example.plimap.domain.auth.service.command.impl.OAuthFailureHandler;
import com.example.plimap.domain.auth.service.command.impl.OAuthSuccessHandler;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.member.service.command.MemberCommandService;
import com.example.plimap.domain.pin.converter.PinConverter;
import com.example.plimap.domain.pin.dto.Pagination;
import com.example.plimap.domain.pin.dto.request.PinRequest;
import com.example.plimap.domain.pin.dto.response.PinResponse;
import com.example.plimap.domain.pin.enums.AvailabilityStatus;
import com.example.plimap.domain.pin.enums.ClusterLevel;
import com.example.plimap.domain.pin.enums.PinSortType;
import com.example.plimap.domain.pin.exception.PinErrorCode;
import com.example.plimap.domain.pin.exception.PinException;
import com.example.plimap.domain.pin.exception.PinLikeException;
import com.example.plimap.domain.pin.repository.query.PinQueryRepository;
import com.example.plimap.domain.pin.service.command.PinCommandService;
import com.example.plimap.domain.pin.service.query.PinQueryService;
import com.example.plimap.global.apiPayload.exception.GlobalExceptionHandler;
import com.example.plimap.global.config.CorsConfig;
import com.example.plimap.global.config.SecurityConfig;
import com.example.plimap.global.security.HttpCookieOAuth2AuthorizationRequestRepository;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.SecurityErrorResponseHandler;
import com.example.plimap.global.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PinController.class)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        SecurityErrorResponseHandler.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class PinControllerTest {

    private static final String PIN_CREATE_ENDPOINT = "/api/v1/pins";
    private static final String PIN_AVAILABILITY_ENDPOINT = "/api/v1/pins/availability";
    private static final String PIN_UPDATE_ENDPOINT = "/api/v1/pins/1";
    private static final String ACCESS_TOKEN = "valid-access-token";
    private static final String MY_FEED_ENDPOINT = "/api/v1/feeds/members/me";
    private static final String MEMBER_FEED_ENDPOINT = "/api/v1/feeds/members/{memberId}";
    private static final String MY_PIN_ENDPOINT = "/api/v1/pins/members/me";
    private static final String PLACE_TRACK_PIN_ENDPOINT = "/api/v1/place-tracks/{placeTrackId}/pins";
    private static final String VIEWPORT_CLUSTER_ENDPOINT = "/api/v1/pins/map";
    private static final String FRIEND_RECENT_PIN_ENDPOINT = "/api/v1/pins/friends";
    private static final String FRIEND_FEED_AUTHORIZE_ENDPOINT = "/api/v1/feeds/places/{placeId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    JwtUtil jwtUtil;

    @MockitoBean
    private CustomOAuthService customOAuthService;

    @MockitoBean
    private OAuthSuccessHandler oAuthSuccessHandler;

    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    private MemberCommandService memberCommandService;

    @MockitoBean
    TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private PinCommandService pinCommandService;

    @MockitoBean
    private PinQueryRepository pinQueryRepository;

    @MockitoBean
    private PinQueryService pinQueryService;

    @MockitoBean
    private OAuthFailureHandler oAuthFailureHandler;

    @MockitoBean
    private HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    @BeforeEach
    void setUp() {
        when(jwtUtil.isValid(ACCESS_TOKEN)).thenReturn(true);
        when(jwtUtil.isAccessToken(ACCESS_TOKEN)).thenReturn(true);
        when(jwtUtil.getJti(ACCESS_TOKEN)).thenReturn("test-jti");
        when(tokenBlacklistService.isBlacklisted("test-jti")).thenReturn(false);
        when(jwtUtil.getMemberId(ACCESS_TOKEN)).thenReturn(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(Member.builder().build()));
    }

    @Test
    void 핀_등록에_성공하면_201을_반환한다() throws Exception {
        when(pinCommandService.createPin(
                any(Member.class),
                any(PinRequest.Create.class)
        )).thenReturn(PinResponse.Summary.builder()
                .pinId(1L)
                .placeId(1L)
                .writerNickname("이서")
                .writerProfileImage("image_url")
                .introduction("feeling love attack!")
                .clipStartMs(70000)
                .build());

        mockMvc.perform(post(PIN_CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_CREATED_SUCCESS"))
                .andExpect(jsonPath("$.message").value("핀이 생성되었습니다."))
                .andExpect(jsonPath("$.result.pinId").value(1))
                .andExpect(jsonPath("$.result.placeId").value(1))
                .andExpect(jsonPath("$.result.writerNickname").value("이서"))
                .andExpect(jsonPath("$.result.writerProfileImage").value("image_url"))
                .andExpect(jsonPath("$.result.introduction").value("feeling love attack!"))
                .andExpect(jsonPath("$.result.clipStartMs").value(70000));
    }

    @Test
    void 동일한_장소에_등록한_핀이_있으면_409를_반환한다() throws Exception {
        when(pinCommandService.createPin(
                any(Member.class),
                any(PinRequest.Create.class)
        )).thenThrow(new PinException(PinErrorCode.MEMBER_PIN_ALREADY_EXISTS));

        mockMvc.perform(post(PIN_CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PIN_MEMBER_PIN_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 해당 장소에 등록한 핀이 있습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 태그_개수가_0개면_400을_반환한다() throws Exception {
        mockMvc.perform(post(PIN_CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTagCreateRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("tags는 1개 이상 4개 이하만 입력할 수 있습니다."));
        verifyNoInteractions(pinCommandService);
    }

    @Test
    void 태그_개수가_5개_이상이면_400을_반환한다() throws Exception {
        mockMvc.perform(post(PIN_CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTagCreateRequest2()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("tags는 1개 이상 4개 이하만 입력할 수 있습니다."));
        verifyNoInteractions(pinCommandService);
    }

    @Test
    void 현위치가_핀위치_거리가_500m_이상일시_400을_반환한다() throws Exception {
        when(pinCommandService.createPin(
                any(Member.class),
                any(PinRequest.Create.class)
        )).thenThrow(new PinException(PinErrorCode.LOCATION_DISTANCE_INVALID));

        mockMvc.perform(post(PIN_CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inValidLocationRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PIN_LOCATION_DISTANCE_INVALID"))
                .andExpect(jsonPath("$.message").value("사용자가 장소 반경이 500m 이상에 있어 PIN을 등록할 수 없습니다."));
    }

    @Test
    void 지도_선택위치_검증_등록가능시_200을_반환한다() throws Exception {
        when(pinQueryService.validatePinAvailability(
                any(PinRequest.PinAvailability.class)
        )).thenReturn(PinResponse.PinAvailability.builder()
                .status(AvailabilityStatus.CREATABLE_NEW_PLACE)
                .registrable(true)
                .distanceFromUserMeters(328.98070823323764)
                .nearestPinDistanceMeters(null)
                .build());

        // CREATABLE_NEW_PLACE
        mockMvc.perform(get(PIN_AVAILABILITY_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("latitude", "37.629000")
                        .param("longitude", "127.094000")
                        .param("userLatitude", "37.626144976334544")
                        .param("userLongitude", "127.09302024107471"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_AVAILABILITY_CHECK_SUCCESS"))
                .andExpect(jsonPath("$.message").value("PIN 등록 가능 여부 검증에 성공했습니다."))
                .andExpect(jsonPath("$.result.status").value("CREATABLE_NEW_PLACE"))
                .andExpect(jsonPath("$.result.registrable").value(true))
                .andExpect(jsonPath("$.result.distanceFromUserMeters").value(328.98070823323764))
                .andExpect(jsonPath("$.result.nearestPinDistanceMeters").doesNotExist());
    }

    @Test
    void 지도_선택위치_검증_500m_초과시_200을_반환한다() throws Exception {
        when(pinQueryService.validatePinAvailability(
                any(PinRequest.PinAvailability.class)
        )).thenReturn(PinResponse.PinAvailability.builder()
                .status(AvailabilityStatus.OUT_OF_RANGE)
                .registrable(false)
                .distanceFromUserMeters(620.0)
                .nearestPinDistanceMeters(null)
                .build());

        // OUT_OF_RANGE
        mockMvc.perform(get(PIN_AVAILABILITY_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("latitude", "37.629000")
                        .param("longitude", "127.094000")
                        .param("userLatitude", "37.626144976334544")
                        .param("userLongitude", "127.09302024107471"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_AVAILABILITY_CHECK_SUCCESS"))
                .andExpect(jsonPath("$.message").value("PIN 등록 가능 여부 검증에 성공했습니다."))
                .andExpect(jsonPath("$.result.status").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.result.registrable").value(false))
                .andExpect(jsonPath("$.result.distanceFromUserMeters").value(620.0))
                .andExpect(jsonPath("$.result.nearestPinDistanceMeters").doesNotExist());
    }

    @Test
    void 지도_선택위치_검증_20m_이내_핀_존재시_200을_반환한다() throws Exception {
        when(pinQueryService.validatePinAvailability(
                any(PinRequest.PinAvailability.class)
        )).thenReturn(PinResponse.PinAvailability.builder()
                .status(AvailabilityStatus.TOO_CLOSE_TO_PIN)
                .registrable(false)
                .distanceFromUserMeters(76.0836069534716)
                .nearestPinDistanceMeters(83.07525620101859)
                .build());

        // TOO_CLOSE_TO_PIN
        mockMvc.perform(get(PIN_AVAILABILITY_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("latitude", "37.629000")
                        .param("longitude", "127.094000")
                        .param("userLatitude", "37.626144976334544")
                        .param("userLongitude", "127.09302024107471"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_AVAILABILITY_CHECK_SUCCESS"))
                .andExpect(jsonPath("$.message").value("PIN 등록 가능 여부 검증에 성공했습니다."))
                .andExpect(jsonPath("$.result.status").value("TOO_CLOSE_TO_PIN"))
                .andExpect(jsonPath("$.result.registrable").value(false))
                .andExpect(jsonPath("$.result.distanceFromUserMeters").value(76.0836069534716))
                .andExpect(jsonPath("$.result.nearestPinDistanceMeters").value(83.07525620101859));
    }

    @Test
    void 핀_수정에_성공하면_200을_반환한다() throws Exception {
        when(pinCommandService.updatePin(
                any(Member.class),
                any(PinRequest.Update.class),
                anyLong()
        )).thenReturn(PinResponse.UpdatedPin.builder()
                .introduction("feeling love attack!")
                .tags(List.of("청량", "설렘"))
                .feedOpen(true)
                .build());

        mockMvc.perform(patch(PIN_UPDATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.message").value("PIN이 수정되었습니다."))
                .andExpect(jsonPath("$.result.introduction").value("feeling love attack!"))
                .andExpect(jsonPath("$.result.tags[0]").value("청량"))
                .andExpect(jsonPath("$.result.tags[1]").value("설렘"))
                .andExpect(jsonPath("$.result.feedOpen").value(true));
    }

    @Test
    void 수정할_값이_없을시_400을_반환한다() throws Exception {
        when(pinCommandService.updatePin(
                any(Member.class),
                any(PinRequest.Update.class),
                anyLong()
        )).thenThrow(new PinException(PinErrorCode.PIN_NOT_CHANGED));

        mockMvc.perform(patch(PIN_UPDATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUpdateRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PIN_NOT_CHANGED"))
                .andExpect(jsonPath("$.message").value("PIN 수정사항이 없습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 내_피드_조회에_성공하면_200을_반환한다() throws Exception {
        when(pinQueryService.findFeedListByMemberId(
                1L, 1L, null, 10, PinRequest.UserLocation.builder()
                        .userLatitude(37.5283)
                        .userLongitude(126.9326)
                        .build()
        )).thenReturn(Pagination.<PinResponse.Feed>builder()
                        .data(new ArrayList<>())
                        .pageSize(10)
                        .nextCursor(null)
                        .hasNext(false)
                .build());

        Member member = Member.builder().build();
        ReflectionTestUtils.setField(member, "id", 1L);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        mockMvc.perform(get(MY_FEED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("userLatitude", "37.5283")
                        .param("userLongitude", "126.9326"))
                        .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value("PIN_MY_FEED_LIST_SEARCH_SUCCESS"))
            .andExpect(jsonPath("$.message").value("내가 작성한 피드 목록이 조회되었습니다."))
            .andExpect(jsonPath("$.result.hasNext").value(false))
            .andExpect(jsonPath("$.result.pageSize").value(10));
    }

    @Test
    void 로그인하지_않은채로_내_피드_조회시_401을_반환한다() throws Exception {
        mockMvc.perform(get(MY_FEED_ENDPOINT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 타인_피드_조회에_성공하면_200을_반환한다() throws Exception {
        when(pinQueryService.findFeedListByMemberId(
                1L, null, null, 10, PinRequest.UserLocation.builder()
                        .userLatitude(37.5283)
                        .userLongitude(126.9326)
                        .build()
        )).thenReturn(Pagination.<PinResponse.Feed>builder()
                .data(new ArrayList<>())
                .pageSize(10)
                .nextCursor(null)
                .hasNext(false)
                .build());

        mockMvc.perform(get(MEMBER_FEED_ENDPOINT, 1L)
                 .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .param("userLatitude", "37.5283")
                .param("userLongitude", "126.9326"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_MEMBER_FEED_LIST_SEARCH_SUCCESS"))
                .andExpect(jsonPath("$.message").value("다른 사용자가 작성한 피드 목록이 조회되었습니다."))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.pageSize").value(10));
    }

    @Test
    void 로그인한_상태로_타인_피드_조회시_viewerId가_전달된다() throws Exception {
        Member viewer = Member.builder().build();
        ReflectionTestUtils.setField(viewer, "id", 1L);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(viewer));
        PinRequest.UserLocation userLocation = PinRequest.UserLocation.builder()
                .userLatitude(37.5283)
                .userLongitude(126.9326)
                .build();
        when(pinQueryService.findFeedListByMemberId(
                2L, 1L, null, 10, userLocation
        )).thenReturn(Pagination.<PinResponse.Feed>builder()
                .data(new ArrayList<>())
                .pageSize(10)
                .nextCursor(null)
                .hasNext(false)
                .build());

        mockMvc.perform(get(MEMBER_FEED_ENDPOINT, 2L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("userLatitude", "37.5283")
                        .param("userLongitude", "126.9326"))
                .andExpect(status().isOk());

        verify(pinQueryService).findFeedListByMemberId(2L, 1L, null, 10, userLocation);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 내_핀_목록_조회에_성공하면_공개_상태와_200을_반환한다(boolean feedOpen) throws Exception {
        when(pinQueryService.findMyPinList(
                1L, null, 10
        )).thenReturn(Pagination.<PinResponse.MyPin>builder()
                .data(List.of(PinResponse.MyPin.builder().pinId(54L).feedOpen(feedOpen).build()))
                .pageSize(10)
                .nextCursor(null)
                .hasNext(false)
                .build());

        Member member = Member.builder().build();
        ReflectionTestUtils.setField(member, "id", 1L);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        mockMvc.perform(get(MY_PIN_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_MY_PIN_LIST_SEARCH_SUCCESS"))
                .andExpect(jsonPath("$.message").value("내가 작성한 핀 목록이 조회되었습니다."))
                .andExpect(jsonPath("$.result.data[0].pinId").value(54))
                .andExpect(jsonPath("$.result.data[0].feedOpen").value(feedOpen))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.pageSize").value(10));
    }

    @Test
    void 범위에서_벗어날시_400을_반환한다() throws Exception {
        mockMvc.perform(get(MY_FEED_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("userLatitude", "101.5283")
                        .param("userLongitude", "126.9326")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUpdateRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 특정_장소_노래의_핀_목록_조회에_성공하면_200을_반환한다() throws Exception {
        given(pinQueryService.findPinListByPlaceTrackIdAndSortType(
                any(Member.class),
                isNull(),
                eq(10),
                eq(PinSortType.LATEST),
                eq(1L),
                any(PinRequest.UserLocation.class),
                eq("token")
        )).willReturn(
                Pagination.<PinResponse.PinDetail>builder()
                        .data(new ArrayList<>())
                        .pageSize(10)
                        .nextCursor(null)
                        .hasNext(false)
                        .build()
        );

        Member member = Member.builder().build();
        ReflectionTestUtils.setField(member, "id", 1L);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        mockMvc.perform(get(PLACE_TRACK_PIN_ENDPOINT, 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header("Place-Access-Token", "token")
                .param("userLatitude", "37.123")
                .param("userLongitude", "127.123"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_PLACE_TRACK_PIN_LIST_SEARCH_SUCCESS"))
                .andExpect(jsonPath("$.message").value("특정 장소 노래의 핀 목록이 조회되었습니다."))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.pageSize").value(10));
    }

    @Test
    void 클러스터_조회에_성공하면_200을_반환한다() throws Exception {
        List<PinResponse.Cluster> clusters = List.of(
                new PinResponse.Cluster(
                        ClusterLevel.REGION1,
                        "서울특별시",
                        null,
                        127.0,
                        37.5,
                        10,
                        new PinResponse.Bound(0D,0D,0D,0D),
                        false
                )
        );

        given(pinQueryService.getClusterPinList(any(), isNull()))
                .willReturn(
                        PinConverter.toClusterAndPin(clusters, null, 7)
                );

        mockMvc.perform(get(VIEWPORT_CLUSTER_ENDPOINT)
                        .param("southWestLat", "36.5")
                        .param("southWestLng", "126.9")
                        .param("northEastLat", "37.6")
                        .param("northEastLng", "127.35")
                        .param("zoomLevel", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_CLUSTER_PIN_SEARCH_SUCCESS"))
                .andExpect(jsonPath("$.message").value("viewport 기반 클러스터&핀 목록이 조회되었습니다."))
                .andExpect(jsonPath("$.result.zoomLevel").value(7))
                .andExpect(jsonPath("$.result.clusters").isArray())
                .andExpect(jsonPath("$.result.pins").doesNotExist());

        verify(pinQueryService).getClusterPinList(any(PinRequest.Viewport.class),  isNull());
    }

    @Test
    void 핀목록_조회에_성공하면_200을_반환한다() throws Exception {
        List<PinResponse.PinPreview> pins = List.of(
                new PinResponse.PinPreview(
                        1L,
                        37.5665,
                        126.9780,
                        1L,
                        "seoyoon",
                        "https://example.com/profile.png",
                        "좋아하는 노래예요",
                        "https://example.com/album.jpg",
                        "dQw4w9WgXcQ",
                        30000,
                        false
                )
        );

        given(pinQueryService.getClusterPinList(any(),  isNull()))
                .willReturn(
                        PinConverter.toClusterAndPin(null, pins, 14)
                );

        mockMvc.perform(get(VIEWPORT_CLUSTER_ENDPOINT)
                        .param("southWestLat", "36.5")
                        .param("southWestLng", "126.9")
                        .param("northEastLat", "37.6")
                        .param("northEastLng", "127.35")
                        .param("zoomLevel", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_CLUSTER_PIN_SEARCH_SUCCESS"))
                .andExpect(jsonPath("$.message").value("viewport 기반 클러스터&핀 목록이 조회되었습니다."))
                .andExpect(jsonPath("$.result.zoomLevel").value(14))
                .andExpect(jsonPath("$.result.clusters").doesNotExist())
                .andExpect(jsonPath("$.result.pins").isArray());

        verify(pinQueryService).getClusterPinList(any(PinRequest.Viewport.class),  isNull());
    }

    @Test
    void 친구_최근핀_조회에_성공하면_200을_반환한다() throws Exception {
        Member member = Member.builder().build();
        ReflectionTestUtils.setField(member, "id", 1L);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        given(pinQueryService.getFriendRecentPinList(eq(1L), isNull(), eq(10)))
                .willReturn(PinConverter.toPagination(
                        List.of(),
                        null,
                        false,
                        10
                ));

        mockMvc.perform(get(FRIEND_RECENT_PIN_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andDo(print())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_FRIENDS_RECENT_LIST_SEARCH_SUCCESS"))
                .andExpect(jsonPath("$.message").value("내 친구 최근 핀 목록이 조회되었습니다."))
                .andExpect(jsonPath("$.result.data").isArray())
                .andExpect(jsonPath("$.result.data.length()").value(0))
                .andExpect(jsonPath("$.result.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.pageSize").value(10));


        verify(pinQueryService)
                .getFriendRecentPinList(eq(1L), isNull(), eq(10));
    }

    @Test
    void 친구_피드_접근_권한_요청_성공시_200을_반환한다() throws Exception{
        Member member = Member.builder().build();
        ReflectionTestUtils.setField(member, "id", 1L);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        given(pinCommandService.createPlaceAccessToken(eq(1L), eq(1L)))
                .willReturn(PinConverter.toPlaceAccessToken(
                        1L,
                        "token"
                ));

        mockMvc.perform(post(FRIEND_FEED_AUTHORIZE_ENDPOINT, 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PIN_FRIEND_FEED_TOKEN_REQUEST_SUCCESS"))
                .andExpect(jsonPath("$.message").value("내 친구 피드 접근 권한 요청에 성공했습니다."))
                .andExpect(jsonPath("$.result.placeAccessToken").value("token"))
                .andExpect(jsonPath("$.result.placeId").value(1L));
    }

    @Test
    void 친구가_등록한_핀이_아니면_403을_반환한다() throws Exception{
        Member member = Member.builder().build();
        ReflectionTestUtils.setField(member, "id", 1L);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        given(pinCommandService.createPlaceAccessToken(anyLong(), anyLong()))
                .willThrow(new PinException(PinErrorCode.FRIEND_PIN_ACCESS_DENIED));

        mockMvc.perform(post(FRIEND_FEED_AUTHORIZE_ENDPOINT, 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PIN_FRIEND_PIN_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("친구가 등록한 핀이 아니므로 접근 권한을 발급할 수 없습니다."));
    }

    private String validCreateRequest() {
        return """
                {
                   "userLatitude": 37.5297,
                   "userLongitude": 126.9333,
                   "placeId": 1,
                   "itunesTrackId": 1764485170,
                   "clipStartMs": 70000,
                   "introduction": "feeling love attack!",
                   "tags": [
                      "몽환"
                   ],
                   "feedOpen": true
                 }
                """;
    }

    private String invalidTagCreateRequest() {
        return """
                {
                   "userLatitude": 37.5297,
                   "userLongitude": 126.9333,
                   "placeId": 1,
                   "itunesTrackId": 1764485170,
                   "clipStartMs": 70000,
                   "introduction": "feeling love attack!",
                   "tags": [],
                   "feedOpen": true
                 }
                """;
    }

    private String invalidTagCreateRequest2() {
        return """
                {
                   "userLatitude": 37.5297,
                   "userLongitude": 126.9333,
                   "placeId": 1,
                   "itunesTrackId": 1764485170,
                   "clipStartMs": 70000,
                   "introduction": "feeling love attack!",
                   "tags": ["몽환", "청량", "신남", "위로", "잔잔"],
                   "feedOpen": true
                 }
                """;
    }

    private String inValidLocationRequest() {
        return """
                {
                   "userLatitude": 37.5370,
                   "userLongitude": 126.9326,
                   "placeId": 1,
                   "itunesTrackId": 1764485170,
                   "clipStartMs": 70000,
                   "introduction": "feeling love attack!",
                   "tags": [
                      "몽환"
                   ],
                   "feedOpen": true
                 }
                """;
    }

    private String validUpdateRequest() {
        return """
                {
                  "introduction": "feeling love attack!",
                  "tags":["청량", "설렘"],
                  "feedOpen": true
                }
                """;
    }

    private String invalidUpdateRequest() {
        return """
                {
                  "introduction": null,
                  "tags":null,
                  "feedOpen": null
                }
                """;
    }
}