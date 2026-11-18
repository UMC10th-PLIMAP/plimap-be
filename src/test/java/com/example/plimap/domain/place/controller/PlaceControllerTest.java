package com.example.plimap.domain.place.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.plimap.domain.auth.service.command.impl.CustomOAuthService;
import com.example.plimap.domain.auth.service.command.impl.OAuthFailureHandler;
import com.example.plimap.domain.auth.service.command.impl.OAuthSuccessHandler;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberRole;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.member.service.command.MemberCommandService;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.domain.place.entity.PlaceSource;
import com.example.plimap.domain.place.enums.MapSelectionStatus;
import com.example.plimap.domain.place.enums.PopularPlaceScope;
import com.example.plimap.domain.place.exception.PlaceErrorCode;
import com.example.plimap.domain.place.exception.PlaceException;
import com.example.plimap.domain.place.service.command.PlaceCommandService;
import com.example.plimap.domain.place.service.query.PlaceQueryService;
import com.example.plimap.global.apiPayload.exception.GlobalExceptionHandler;
import com.example.plimap.global.config.CorsConfig;
import com.example.plimap.global.config.SecurityConfig;
import com.example.plimap.global.security.AuthCookieUtil;
import com.example.plimap.global.security.HttpCookieOAuth2AuthorizationRequestRepository;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.SecurityErrorResponseHandler;
import com.example.plimap.global.security.TokenBlacklistService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PlaceController.class)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        SecurityErrorResponseHandler.class,
        AuthCookieUtil.class,
        HttpCookieOAuth2AuthorizationRequestRepository.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class PlaceControllerTest {

    private static final String MAP_SELECTION_ENDPOINT = "/api/v1/places/map-selections";
    private static final String SELECTION_ENDPOINT = "/api/v1/places/selections";
    private static final String SEARCH_ENDPOINT = "/api/v1/places/search";
    private static final String DETAIL_ENDPOINT = "/api/v1/places/1";
    private static final String BOOKMARK_ENDPOINT = "/api/v1/places/1/bookmarks";
    private static final String BOOKMARK_LIST_ENDPOINT = "/api/v1/places/bookmarks";
    private static final String POPULAR_ENDPOINT = "/api/v1/places/popular";
    private static final String ACCESS_TOKEN = "valid-access-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceCommandService placeCommandService;

    @MockitoBean
    private PlaceQueryService placeQueryService;

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

    @BeforeEach
    void setUp() {
        when(jwtUtil.isValid(ACCESS_TOKEN)).thenReturn(true);
        when(jwtUtil.isAccessToken(ACCESS_TOKEN)).thenReturn(true);
        when(jwtUtil.getJti(ACCESS_TOKEN)).thenReturn("test-jti");
        when(tokenBlacklistService.isBlacklisted("test-jti")).thenReturn(false);
        when(jwtUtil.getMemberId(ACCESS_TOKEN)).thenReturn(1L);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getRole()).thenReturn(MemberRole.USER);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
    }

    @Test
    void 인기_장소_목록_조회에_성공하면_명세_응답을_반환한다() throws Exception {
        when(placeQueryService.getPopularPlaces(
                1L,
                PopularPlaceScope.NEARBY,
                37.5283,
                126.9326
        )).thenReturn(new PlaceResponse.PopularListResult(null, null, List.of(
                new PlaceResponse.PopularListItem(
                        1L,
                        "뚝섬한강공원",
                        50,
                        30L,
                        "https://image/1"
                ),
                new PlaceResponse.PopularListItem(2L, "이미지 없는 장소", 120, 20L, null)
        )));

        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("scope", "NEARBY")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PLACE_POPULAR_LIST_SUCCESS"))
                .andExpect(jsonPath("$.message").value("인기 장소 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.result.scopeLevel").value(nullValue()))
                .andExpect(jsonPath("$.result.scopeName").value(nullValue()))
                .andExpect(jsonPath("$.result.items[0].placeId").value(1))
                .andExpect(jsonPath("$.result.items[0].placeName").value("뚝섬한강공원"))
                .andExpect(jsonPath("$.result.items[0].distanceMeters").value(50))
                .andExpect(jsonPath("$.result.items[0].pinCount").value(30))
                .andExpect(jsonPath("$.result.items[0].representativeImageUrl")
                        .value("https://image/1"))
                .andExpect(jsonPath("$.result.items[1].representativeImageUrl")
                        .value(nullValue()));

        verify(placeQueryService).getPopularPlaces(
                1L,
                PopularPlaceScope.NEARBY,
                37.5283,
                126.9326
        );
    }

    @Test
    void 인기_장소_목록의_scope가_잘못되면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("scope", "LOCAL")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 인기_장소_목록의_scope와_좌표가_누락되거나_범위를_벗어나면_공통_400을_반환한다()
            throws Exception {
        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"));

        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("scope", "NEARBY")
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"));

        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("scope", "GLOBAL")
                        .param("latitude", "37.5283"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"));

        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("scope", "GLOBAL")
                        .param("latitude", "90.1")
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"));

        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("scope", "NEARBY")
                        .param("latitude", "37.5283")
                        .param("longitude", "-180.1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 인기_장소_목록은_미인증_요청에_401을_반환한다() throws Exception {
        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .param("scope", "GLOBAL")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"));
    }

    @Test
    void 인기_장소_행정구역_변환_오류는_502를_반환한다() throws Exception {
        when(placeQueryService.getPopularPlaces(
                1L,
                PopularPlaceScope.GLOBAL, 37.5283, 126.9326
        )).thenThrow(new PlaceException(PlaceErrorCode.PLACE_EXTERNAL_API_ERROR));

        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("scope", "GLOBAL")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PLACE_EXTERNAL_API_ERROR"));
    }

    @Test
    void 인기_장소_행정구역_변환_timeout은_504를_반환한다() throws Exception {
        when(placeQueryService.getPopularPlaces(
                1L,
                PopularPlaceScope.GLOBAL, 37.5283, 126.9326
        )).thenThrow(new PlaceException(PlaceErrorCode.PLACE_EXTERNAL_API_TIMEOUT));

        mockMvc.perform(get(POPULAR_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("scope", "GLOBAL")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PLACE_EXTERNAL_API_TIMEOUT"));
    }

    @Test
    void 저장한_장소_목록_조회에_성공하면_명세_응답을_반환한다() throws Exception {
        when(placeQueryService.getPlaceBookmarks(1L, 37.5283, 126.9326))
                .thenReturn(new PlaceResponse.BookmarkListResult(List.of(
                        new PlaceResponse.BookmarkListItem(
                                1L,
                                "물빛무대 앞 광장",
                                "홍길동",
                                470
                        ),
                        new PlaceResponse.BookmarkListItem(
                                2L,
                                "뚝섬역 2호선",
                                null,
                                480
                        )
                )));

        mockMvc.perform(get(BOOKMARK_LIST_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PLACE_BOOKMARK_LIST_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("저장한 장소 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.result.items.length()").value(2))
                .andExpect(jsonPath("$.result.items[0].placeId").value(1))
                .andExpect(jsonPath("$.result.items[0].placeName")
                        .value("물빛무대 앞 광장"))
                .andExpect(jsonPath("$.result.items[0].firstPinCreatorNickname")
                        .value("홍길동"))
                .andExpect(jsonPath("$.result.items[0].distanceMeters").value(470))
                .andExpect(jsonPath("$.result.items[1].firstPinCreatorNickname")
                        .value(nullValue()));

        verify(placeQueryService).getPlaceBookmarks(1L, 37.5283, 126.9326);
    }

    @Test
    void 저장한_장소_목록은_빈_items를_반환한다() throws Exception {
        when(placeQueryService.getPlaceBookmarks(1L, 37.5283, 126.9326))
                .thenReturn(new PlaceResponse.BookmarkListResult(List.of()));

        mockMvc.perform(get(BOOKMARK_LIST_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isArray())
                .andExpect(jsonPath("$.result.items").isEmpty());
    }

    @Test
    void 저장한_장소_목록에서_좌표가_누락되면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(get(BOOKMARK_LIST_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 저장한_장소_목록에서_좌표가_범위를_벗어나면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(get(BOOKMARK_LIST_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("latitude", "91")
                        .param("longitude", "181"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 저장한_장소_목록은_미인증_요청에_401을_반환한다() throws Exception {
        mockMvc.perform(get(BOOKMARK_LIST_ENDPOINT)
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 장소_북마크_등록에_성공하면_명세_응답을_반환한다() throws Exception {
        when(placeCommandService.bookmarkPlace(1L, 1L))
                .thenReturn(new PlaceResponse.BookmarkResult(1L, true));

        mockMvc.perform(put(BOOKMARK_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PLACE_BOOKMARK_CREATE_SUCCESS"))
                .andExpect(jsonPath("$.message").value("장소 북마크 등록에 성공했습니다."))
                .andExpect(jsonPath("$.result.placeId").value(1))
                .andExpect(jsonPath("$.result.bookmarkedByMe").value(true));

        verify(placeCommandService).bookmarkPlace(1L, 1L);
    }

    @Test
    void 장소_북마크_삭제에_성공하면_명세_응답을_반환한다() throws Exception {
        when(placeCommandService.deletePlaceBookmark(1L, 1L))
                .thenReturn(new PlaceResponse.BookmarkResult(1L, false));

        mockMvc.perform(delete(BOOKMARK_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PLACE_BOOKMARK_DELETE_SUCCESS"))
                .andExpect(jsonPath("$.message").value("장소 북마크 삭제에 성공했습니다."))
                .andExpect(jsonPath("$.result.placeId").value(1))
                .andExpect(jsonPath("$.result.bookmarkedByMe").value(false));

        verify(placeCommandService).deletePlaceBookmark(1L, 1L);
    }

    @Test
    void 장소_북마크_등록에서_장소가_없으면_404를_반환한다() throws Exception {
        when(placeCommandService.bookmarkPlace(1L, 1L))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_NOT_FOUND));

        mockMvc.perform(put(BOOKMARK_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void 장소_북마크_삭제에서_장소가_없으면_404를_반환한다() throws Exception {
        when(placeCommandService.deletePlaceBookmark(1L, 1L))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_NOT_FOUND));

        mockMvc.perform(delete(BOOKMARK_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void 장소_북마크_등록과_삭제는_인증_실패시_공통_401을_반환한다() throws Exception {
        mockMvc.perform(put(BOOKMARK_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"));
        mockMvc.perform(delete(BOOKMARK_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"));

        verifyNoInteractions(placeCommandService);
    }

    @Test
    void 장소_상세_조회에_성공하면_명세_응답을_반환한다() throws Exception {
        when(placeQueryService.getPlaceDetail(1L, 1L, 37.5283, 126.9326))
                .thenReturn(new PlaceResponse.Detail(
                        1L,
                        "한강",
                        "공원",
                        "서울특별시 영등포구 여의도동",
                        "서울특별시 영등포구 여의동로",
                        37.5283,
                        126.9326,
                        470,
                        true,
                        true,
                        "홍길동",
                        3L,
                        false,
                        true
                ));

        mockMvc.perform(validDetailRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PLACE_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.message").value("장소 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.result.placeId").value(1))
                .andExpect(jsonPath("$.result.placeName").value("한강"))
                .andExpect(jsonPath("$.result.category").value("공원"))
                .andExpect(jsonPath("$.result.address")
                        .value("서울특별시 영등포구 여의도동"))
                .andExpect(jsonPath("$.result.roadAddress")
                        .value("서울특별시 영등포구 여의동로"))
                .andExpect(jsonPath("$.result.latitude").value(37.5283))
                .andExpect(jsonPath("$.result.longitude").value(126.9326))
                .andExpect(jsonPath("$.result.distanceMeters").value(470))
                .andExpect(jsonPath("$.result.withinAccessRange").value(true))
                .andExpect(jsonPath("$.result.hasPin").value(true))
                .andExpect(jsonPath("$.result.firstPinCreatorNickname").value("홍길동"))
                .andExpect(jsonPath("$.result.pinCount").value(3))
                .andExpect(jsonPath("$.result.bookmarkedByMe").value(false))
                .andExpect(jsonPath("$.result.pinnedByMe").value(true))
                .andExpect(jsonPath("$.result.detailAccessible").doesNotExist())
                .andExpect(jsonPath("$.result.likedTrackAtPlaceByMe").doesNotExist())
                .andExpect(jsonPath("$.result.followedMemberPinnedAtPlace").doesNotExist());

        verify(placeQueryService).getPlaceDetail(1L, 1L, 37.5283, 126.9326);
    }

    @Test
    void 장소_상세_조회에서_장소가_없으면_404를_반환한다() throws Exception {
        when(placeQueryService.getPlaceDetail(1L, 1L, 37.5283, 126.9326))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_NOT_FOUND));

        mockMvc.perform(validDetailRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("장소를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void 장소_상세_조회에서_위도가_누락되면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(get(DETAIL_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 장소_상세_조회에서_경도가_누락되면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(get(DETAIL_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("latitude", "37.5283"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 장소_상세_조회에서_좌표가_범위를_벗어나면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(get(DETAIL_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("latitude", "91")
                        .param("longitude", "181"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 장소_상세_조회는_인증되지_않은_요청에_401을_반환한다() throws Exception {
        mockMvc.perform(get(DETAIL_ENDPOINT)
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void MAP_SELECTION_장소_판정에_성공하면_확정_응답과_200을_반환한다() throws Exception {
        when(placeCommandService.confirmMapSelection(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PlaceResponse.MapSelectionResult(
                        MapSelectionStatus.MAP_SELECTION_CONFIRMED,
                        new PlaceResponse.MapSelection(
                                12L,
                                "영등포구 여의동로 123-4",
                                PlaceSource.MAP_SELECTION,
                                37.5283,
                                126.9326
                        ),
                        null,
                        null
                ));

        mockMvc.perform(post(MAP_SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PLACE_MAP_SELECTION_SUCCESS"))
                .andExpect(jsonPath("$.message").value("지도 선택 장소 판정에 성공했습니다."))
                .andExpect(jsonPath("$.result.status").value("MAP_SELECTION_CONFIRMED"))
                .andExpect(jsonPath("$.result.mapSelection.placeId").value(12))
                .andExpect(jsonPath("$.result.mapSelection.placeName")
                        .value("영등포구 여의동로 123-4"))
                .andExpect(jsonPath("$.result.mapSelection.source").value("MAP_SELECTION"))
                .andExpect(jsonPath("$.result.mapSelection.latitude").value(37.5283))
                .andExpect(jsonPath("$.result.mapSelection.longitude").value(126.9326))
                .andExpect(jsonPath("$.result.recommendedPlace").value(nullValue()))
                .andExpect(jsonPath("$.result.buildingName").value(nullValue()));
    }

    @Test
    void 기존_PLACE_SEARCH_장소를_추천하면_추천_응답과_200을_반환한다() throws Exception {
        when(placeCommandService.confirmMapSelection(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PlaceResponse.MapSelectionResult(
                        MapSelectionStatus.PLACE_SEARCH_RECOMMENDED,
                        null,
                        new PlaceResponse.RecommendedPlace(
                                10L,
                                "카카오 판교아지트",
                                null,
                                "경기도 성남시 분당구 백현동 532",
                                null,
                                PlaceSource.PLACE_SEARCH,
                                37.3947,
                                127.1112,
                                12
                        ),
                        null
                ));

        mockMvc.perform(post(MAP_SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLACE_MAP_SELECTION_SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("PLACE_SEARCH_RECOMMENDED"))
                .andExpect(jsonPath("$.result.mapSelection").value(nullValue()))
                .andExpect(jsonPath("$.result.recommendedPlace.placeId").value(10))
                .andExpect(jsonPath("$.result.recommendedPlace.placeName")
                        .value("카카오 판교아지트"))
                .andExpect(jsonPath("$.result.recommendedPlace.category").value(nullValue()))
                .andExpect(jsonPath("$.result.recommendedPlace.address")
                        .value("경기도 성남시 분당구 백현동 532"))
                .andExpect(jsonPath("$.result.recommendedPlace.roadAddress").value(nullValue()))
                .andExpect(jsonPath("$.result.recommendedPlace.source").value("PLACE_SEARCH"))
                .andExpect(jsonPath("$.result.recommendedPlace.latitude").value(37.3947))
                .andExpect(jsonPath("$.result.recommendedPlace.longitude").value(127.1112))
                .andExpect(jsonPath("$.result.recommendedPlace.distanceMeters").value(12))
                .andExpect(jsonPath("$.result.buildingName").value(nullValue()));
    }

    @Test
    void 건물명으로_장소_검색이_필요하면_검색_필요_응답과_200을_반환한다() throws Exception {
        when(placeCommandService.confirmMapSelection(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PlaceResponse.MapSelectionResult(
                        MapSelectionStatus.PLACE_SEARCH_REQUIRED,
                        null,
                        null,
                        "카카오 판교아지트"
                ));

        mockMvc.perform(post(MAP_SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLACE_MAP_SELECTION_SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("PLACE_SEARCH_REQUIRED"))
                .andExpect(jsonPath("$.result.mapSelection").value(nullValue()))
                .andExpect(jsonPath("$.result.recommendedPlace").value(nullValue()))
                .andExpect(jsonPath("$.result.buildingName").value("카카오 판교아지트"));
    }

    @Test
    void 지도_선택_장소_판정에서_Kakao_오류가_발생하면_502를_반환한다() throws Exception {
        when(placeCommandService.confirmMapSelection(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_EXTERNAL_API_ERROR));

        mockMvc.perform(post(MAP_SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PLACE_EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void 지도_선택_장소_판정에서_Kakao_timeout이_발생하면_504를_반환한다() throws Exception {
        when(placeCommandService.confirmMapSelection(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_EXTERNAL_API_TIMEOUT));

        mockMvc.perform(post(MAP_SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PLACE_EXTERNAL_API_TIMEOUT"))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void 좌표가_유효_범위를_벗어나면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(post(MAP_SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 91,
                                  "longitude": 126.9326,
                                  "address": "서울특별시 영등포구 여의도동"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."))
                .andExpect(jsonPath("$.result").isEmpty());

        verifyNoInteractions(placeCommandService);
    }

    @Test
    void address가_blank이면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(post(MAP_SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 37.5283,
                                  "longitude": 126.9326,
                                  "address": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."));

        verifyNoInteractions(placeCommandService);
    }

    @Test
    void 유효하지_않은_Bearer_인증은_공통_401을_반환한다() throws Exception {
        mockMvc.perform(post(MAP_SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"))
                .andExpect(jsonPath("$.result").isEmpty());

        verifyNoInteractions(placeCommandService);
    }

    @Test
    void 장소_검색에_성공하면_명세_응답을_반환한다() throws Exception {
        when(placeQueryService.searchPlaces(any())).thenReturn(new PlaceResponse.SearchResult(
                java.util.List.of(new PlaceResponse.SearchItem(
                        "PLACE",
                        "KAKAO",
                        "26338954",
                        "한강",
                        "여행 > 관광,명소 > 공원",
                        "서울특별시 영등포구 여의도동",
                        "서울특별시 영등포구 여의동로",
                        37.5283,
                        126.9326,
                        470,
                        true,
                        "홍길동"
                ))
        ));

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("keyword", "한강")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_SUCCESS"))
                .andExpect(jsonPath("$.message").value("장소 검색에 성공했습니다."))
                .andExpect(jsonPath("$.result.items[0].resultType").value("PLACE"))
                .andExpect(jsonPath("$.result.items[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$.result.items[0].providerPlaceId").value("26338954"))
                .andExpect(jsonPath("$.result.items[0].placeName").value("한강"))
                .andExpect(jsonPath("$.result.items[0].category")
                        .value("여행 > 관광,명소 > 공원"))
                .andExpect(jsonPath("$.result.items[0].address")
                        .value("서울특별시 영등포구 여의도동"))
                .andExpect(jsonPath("$.result.items[0].roadAddress")
                        .value("서울특별시 영등포구 여의동로"))
                .andExpect(jsonPath("$.result.items[0].latitude").value(37.5283))
                .andExpect(jsonPath("$.result.items[0].longitude").value(126.9326))
                .andExpect(jsonPath("$.result.items[0].distanceMeters").value(470))
                .andExpect(jsonPath("$.result.items[0].hasPin").value(true))
                .andExpect(jsonPath("$.result.items[0].firstPinCreatorNickname")
                        .value("홍길동"));
    }

    @Test
    void 주소_검색에_성공하면_ADDRESS_JSON_계약을_반환한다() throws Exception {
        when(placeQueryService.searchPlaces(any())).thenReturn(new PlaceResponse.SearchResult(
                java.util.List.of(new PlaceResponse.SearchItem(
                        "ADDRESS",
                        "KAKAO",
                        null,
                        "서울특별시 영등포구 여의동로 330",
                        null,
                        "서울특별시 영등포구 여의도동 84",
                        "서울특별시 영등포구 여의동로 330",
                        37.5283,
                        126.9326,
                        470,
                        false,
                        null
                ))
        ));

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("keyword", "여의도동 84")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].resultType").value("ADDRESS"))
                .andExpect(jsonPath("$.result.items[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$.result.items[0].providerPlaceId").value(nullValue()))
                .andExpect(jsonPath("$.result.items[0].placeName")
                        .value("서울특별시 영등포구 여의동로 330"))
                .andExpect(jsonPath("$.result.items[0].category").value(nullValue()))
                .andExpect(jsonPath("$.result.items[0].address")
                        .value("서울특별시 영등포구 여의도동 84"))
                .andExpect(jsonPath("$.result.items[0].roadAddress")
                        .value("서울특별시 영등포구 여의동로 330"))
                .andExpect(jsonPath("$.result.items[0].latitude").value(37.5283))
                .andExpect(jsonPath("$.result.items[0].longitude").value(126.9326))
                .andExpect(jsonPath("$.result.items[0].distanceMeters").value(470))
                .andExpect(jsonPath("$.result.items[0].hasPin").value(false))
                .andExpect(jsonPath("$.result.items[0].firstPinCreatorNickname")
                        .value(nullValue()));
    }

    @Test
    void 장소_검색_좌표가_범위를_벗어나면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("keyword", "한강")
                        .param("latitude", "91")
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 장소_검색_경도가_범위를_벗어나면_공통_400을_반환한다() throws Exception {
        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("keyword", "한강")
                        .param("latitude", "37.5283")
                        .param("longitude", "181"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("위치 정보가 올바르지 않습니다."));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 장소_검색어가_비어_있으면_명세_400을_반환한다() throws Exception {
        when(placeQueryService.searchPlaces(any()))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_SEARCH_KEYWORD_REQUIRED));

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("keyword", "   ")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_KEYWORD_REQUIRED"))
                .andExpect(jsonPath("$.message").value("검색어를 입력해주세요."));
    }

    @Test
    void 장소_검색_현재_위치가_없으면_명세_400을_반환한다() throws Exception {
        when(placeQueryService.searchPlaces(any()))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_CURRENT_LOCATION_REQUIRED));

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("keyword", "한강")
                        .param("longitude", "126.9326"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_CURRENT_LOCATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("현재 위치 정보가 필요합니다."));
    }

    @Test
    void 장소_검색_경도가_없으면_명세_400을_반환한다() throws Exception {
        when(placeQueryService.searchPlaces(any()))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_CURRENT_LOCATION_REQUIRED));

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .param("keyword", "한강")
                        .param("latitude", "37.5283"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_CURRENT_LOCATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("현재 위치 정보가 필요합니다."));
    }

    @Test
    void 카카오_연동_오류는_명세_502를_반환한다() throws Exception {
        when(placeQueryService.searchPlaces(any()))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_EXTERNAL_API_ERROR));

        mockMvc.perform(validSearchRequest())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PLACE_EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("장소 검색 서비스 연동에 실패했습니다."));
    }

    @Test
    void 카카오_timeout은_명세_504를_반환한다() throws Exception {
        when(placeQueryService.searchPlaces(any()))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_EXTERNAL_API_TIMEOUT));

        mockMvc.perform(validSearchRequest())
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PLACE_EXTERNAL_API_TIMEOUT"))
                .andExpect(jsonPath("$.message")
                        .value("장소 검색 서비스 응답이 지연되고 있습니다."));
    }

    @Test
    void 장소_검색의_유효하지_않은_Bearer_인증은_공통_401을_반환한다() throws Exception {
        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .param("keyword", "한강")
                        .param("latitude", "37.5283")
                        .param("longitude", "126.9326"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"));

        verifyNoInteractions(placeQueryService);
    }

    @Test
    void 검색_장소_선택에_성공하면_명세_응답을_반환한다() throws Exception {
        when(placeCommandService.selectSearchPlace(any(), any()))
                .thenReturn(new PlaceResponse.Selection(
                        1L,
                        "한강",
                        "서울특별시 영등포구 여의도동",
                        "서울특별시 영등포구 여의동로",
                        PlaceSource.PLACE_SEARCH,
                        470,
                        true,
                        true,
                        "홍길동",
                        3L,
                        false
                ));

        mockMvc.perform(post(SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSelectionRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PLACE_SELECTION_SUCCESS"))
                .andExpect(jsonPath("$.message").value("장소 선택에 성공했습니다."))
                .andExpect(jsonPath("$.result.placeId").value(1))
                .andExpect(jsonPath("$.result.placeName").value("한강"))
                .andExpect(jsonPath("$.result.address")
                        .value("서울특별시 영등포구 여의도동"))
                .andExpect(jsonPath("$.result.roadAddress")
                        .value("서울특별시 영등포구 여의동로"))
                .andExpect(jsonPath("$.result.source").value("PLACE_SEARCH"))
                .andExpect(jsonPath("$.result.distanceMeters").value(470))
                .andExpect(jsonPath("$.result.withinAccessRange").value(true))
                .andExpect(jsonPath("$.result.hasPin").value(true))
                .andExpect(jsonPath("$.result.firstPinCreatorNickname").value("홍길동"))
                .andExpect(jsonPath("$.result.pinCount").value(3))
                .andExpect(jsonPath("$.result.bookmarkedByMe").value(false));
    }

    @Test
    void 도로명_주소가_없으면_전체_지번_주소와_null을_반환한다() throws Exception {
        when(placeCommandService.selectSearchPlace(any(), any()))
                .thenReturn(new PlaceResponse.Selection(
                        1L,
                        "한강",
                        "서울특별시 영등포구 여의도동",
                        null,
                        PlaceSource.PLACE_SEARCH,
                        470,
                        true,
                        false,
                        null,
                        0L,
                        false
                ));

        mockMvc.perform(post(SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSelectionRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.address")
                        .value("서울특별시 영등포구 여의도동"))
                .andExpect(jsonPath("$.result.roadAddress").value(nullValue()));
    }

    @Test
    void 검색_장소_필수_정보가_올바르지_않으면_명세_400을_반환한다() throws Exception {
        when(placeCommandService.selectSearchPlace(any(), any()))
                .thenThrow(new PlaceException(PlaceErrorCode.PLACE_SELECTION_INVALID));

        mockMvc.perform(post(SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultType": "PLACE",
                                  "provider": "KAKAO",
                                  "providerPlaceId": "26338954",
                                  "placeName": "한강",
                                  "latitude": 37.5283,
                                  "longitude": 126.9326,
                                  "userLatitude": 37.5251,
                                  "userLongitude": 126.9298
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PLACE_SELECTION_INVALID"))
                .andExpect(jsonPath("$.message").value("장소 선택 정보가 올바르지 않습니다."))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void 검색_장소_선택의_유효하지_않은_Bearer_인증은_공통_401을_반환한다()
            throws Exception {
        mockMvc.perform(post(SELECTION_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSelectionRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_401_UNAUTHORIZED"))
                .andExpect(jsonPath("$.result").isEmpty());

        verifyNoInteractions(placeCommandService);
    }

    private String validRequest() {
        return """
                {
                  "latitude": 37.5283,
                  "longitude": 126.9326,
                  "address": "서울특별시 영등포구 여의도동 123-4",
                  "roadAddress": "서울특별시 영등포구 여의동로 123-4"
                }
                """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            validSearchRequest() {
        return get(SEARCH_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .param("keyword", "한강")
                .param("latitude", "37.5283")
                .param("longitude", "126.9326");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            validDetailRequest() {
        return get(DETAIL_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .param("latitude", "37.5283")
                .param("longitude", "126.9326");
    }

    private String validSelectionRequest() {
        return """
                {
                  "resultType": "PLACE",
                  "provider": "KAKAO",
                  "providerPlaceId": "26338954",
                  "placeName": "한강",
                  "category": "공원",
                  "address": "서울특별시 영등포구 여의도동",
                  "roadAddress": "서울특별시 영등포구 여의동로",
                  "latitude": 37.5283,
                  "longitude": 126.9326,
                  "userLatitude": 37.5251,
                  "userLongitude": 126.9298
                }
                """;
    }
}
