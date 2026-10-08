package com.example.plimap.domain.place.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.plimap.domain.pin.dto.PlacePinInfo;
import com.example.plimap.domain.pin.service.query.PinQueryService;
import com.example.plimap.domain.place.dto.NearbyBookmarkedPlace;
import com.example.plimap.domain.place.dto.PlaceAdministrativeRegion;
import com.example.plimap.domain.place.dto.PopularPlaceCandidate;
import com.example.plimap.domain.place.dto.request.PlaceRequest;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.place.entity.PlaceBookmarkId;
import com.example.plimap.domain.place.enums.PopularPlaceScope;
import com.example.plimap.domain.place.enums.PopularPlaceScopeLevel;
import com.example.plimap.domain.place.exception.PlaceErrorCode;
import com.example.plimap.domain.place.exception.PlaceException;
import com.example.plimap.domain.place.repository.PlaceBookmarkRepository;
import com.example.plimap.domain.place.repository.PlaceRepository;
import com.example.plimap.domain.place.repository.PlaceSearchHistoryRepository;
import com.example.plimap.domain.place.repository.query.PlaceBookmarkQueryRepository;
import com.example.plimap.domain.place.repository.query.PlaceQueryRepository;
import com.example.plimap.domain.place.repository.query.PopularPlaceQueryRepository;
import com.example.plimap.domain.place.service.query.impl.PlaceQueryServiceImpl;
import com.example.plimap.domain.track.dto.AlbumImage;
import com.example.plimap.global.external.kakao.KakaoAddressSearchClient;
import com.example.plimap.global.external.kakao.KakaoClientException;
import com.example.plimap.global.external.kakao.KakaoClientTimeoutException;
import com.example.plimap.global.external.kakao.KakaoPlaceSearchClient;
import com.example.plimap.global.external.kakao.dto.KakaoAddressSearchResponse;
import com.example.plimap.global.external.kakao.dto.KakaoPlaceSearchResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.locationtech.jts.geom.Point;

@ExtendWith(MockitoExtension.class)
class PlaceQueryServiceImplTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceBookmarkRepository placeBookmarkRepository;

    @Mock
    private PlaceSearchHistoryRepository placeSearchHistoryRepository;

    @Mock
    private PlaceQueryRepository placeQueryRepository;

    @Mock
    private PlaceBookmarkQueryRepository placeBookmarkQueryRepository;

    @Mock
    private PopularPlaceQueryRepository popularPlaceQueryRepository;

    @Mock
    private PlaceLocationMetadataService placeLocationMetadataService;

    @Mock
    private KakaoAddressSearchClient kakaoAddressSearchClient;

    @Mock
    private KakaoPlaceSearchClient kakaoPlaceSearchClient;

    @Mock
    private PinQueryService pinQueryService;

    private PlaceQueryServiceImpl placeQueryService;

    @BeforeEach
    void setUp() {
        placeQueryService = new PlaceQueryServiceImpl(
                placeRepository,
                placeBookmarkRepository,
                placeSearchHistoryRepository,
                placeQueryRepository,
                placeBookmarkQueryRepository,
                popularPlaceQueryRepository,
                placeLocationMetadataService,
                kakaoAddressSearchClient,
                kakaoPlaceSearchClient,
                pinQueryService
        );
        lenient().when(kakaoAddressSearchClient.search(any()))
                .thenReturn(new KakaoAddressSearchResponse(List.of()));
    }

    @Test
    void NEARBY_인기_장소를_조회하고_대표_이미지를_한_번의_배치_조회로_병합한다() {
        List<PopularPlaceCandidate> candidates = List.of(
                new PopularPlaceCandidate(1L, "첫 장소", 49.6, 3L),
                new PopularPlaceCandidate(2L, "URL 없는 장소", 120.4, 2L),
                new PopularPlaceCandidate(3L, "대표곡 없는 장소", 200.2, 1L)
        );
        when(popularPlaceQueryRepository.findNearbyPopularPlaces(37.5283, 126.9326))
                .thenReturn(candidates);
        when(pinQueryService.findRepresentativePlaceTracksByPlaceIds(List.of(1L, 2L, 3L)))
                .thenReturn(Map.of(
                        1L, new AlbumImage("https://image/1"),
                        2L, new AlbumImage(null)
                ));

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                PopularPlaceScope.NEARBY,
                37.5283,
                126.9326
        );

        assertThat(result.items()).containsExactly(
                new PlaceResponse.PopularListItem(
                        1L,
                        "첫 장소",
                        50,
                        3L,
                        "https://image/1"
                ),
                new PlaceResponse.PopularListItem(2L, "URL 없는 장소", 120, 2L, null),
                new PlaceResponse.PopularListItem(3L, "대표곡 없는 장소", 200, 1L, null)
        );
        assertThat(result.scopeLevel()).isNull();
        assertThat(result.scopeName()).isNull();
        verify(popularPlaceQueryRepository)
                .findNearbyPopularPlaces(37.5283, 126.9326);
        verify(pinQueryService)
                .findRepresentativePlaceTracksByPlaceIds(List.of(1L, 2L, 3L));
    }

    @Test
    void GLOBAL은_REGION3에_6개가_있으면_해당_결과에서_중단한다() {
        List<PopularPlaceCandidate> candidates = popularCandidates(1L);
        when(placeLocationMetadataService.getAdministrativeRegion(37.5283, 126.9326))
                .thenReturn(region());
        when(popularPlaceQueryRepository.findRegion3PopularPlaces(
                "1168010100", 37.5283, 126.9326
        )).thenReturn(candidates);
        when(pinQueryService.findRepresentativePlaceTracksByPlaceIds(
                List.of(1L, 2L, 3L, 4L, 5L, 6L)
        ))
                .thenReturn(Map.of());

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                PopularPlaceScope.GLOBAL,
                37.5283,
                126.9326
        );

        assertThat(result.scopeLevel()).isEqualTo(PopularPlaceScopeLevel.REGION3);
        assertThat(result.scopeName()).isEqualTo("역삼1동");
        assertThat(result.items()).hasSize(6);
        verify(popularPlaceQueryRepository).findRegion3PopularPlaces(
                "1168010100", 37.5283, 126.9326
        );
    }

    @Test
    void GLOBAL은_이전_단계가_6개_미만이면_버리고_REGION2를_다시_조회한다() {
        List<PopularPlaceCandidate> region3Candidates = popularCandidates(1L).subList(0, 5);
        List<PopularPlaceCandidate> region2Candidates = popularCandidates(11L);
        when(placeLocationMetadataService.getAdministrativeRegion(37.5283, 126.9326))
                .thenReturn(region());
        when(popularPlaceQueryRepository.findRegion3PopularPlaces(
                "1168010100", 37.5283, 126.9326
        )).thenReturn(region3Candidates);
        when(popularPlaceQueryRepository.findRegion2PopularPlaces(
                "서울특별시", "강남구", 37.5283, 126.9326
        )).thenReturn(region2Candidates);
        when(pinQueryService.findRepresentativePlaceTracksByPlaceIds(
                List.of(11L, 12L, 13L, 14L, 15L, 16L)
        )).thenReturn(Map.of());

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                PopularPlaceScope.GLOBAL,
                37.5283,
                126.9326
        );

        assertThat(result.scopeLevel()).isEqualTo(PopularPlaceScopeLevel.REGION2);
        assertThat(result.scopeName()).isEqualTo("강남구");
        assertThat(result.items()).extracting(PlaceResponse.PopularListItem::placeId)
                .containsExactly(11L, 12L, 13L, 14L, 15L, 16L);
        verify(pinQueryService).findRepresentativePlaceTracksByPlaceIds(
                List.of(11L, 12L, 13L, 14L, 15L, 16L)
        );
    }

    @Test
    void GLOBAL은_REGION2도_부족하면_REGION1에서_중단한다() {
        List<PopularPlaceCandidate> region1Candidates = popularCandidates(21L);
        when(placeLocationMetadataService.getAdministrativeRegion(37.5283, 126.9326))
                .thenReturn(region());
        when(popularPlaceQueryRepository.findRegion3PopularPlaces(
                "1168010100", 37.5283, 126.9326
        )).thenReturn(List.of());
        when(popularPlaceQueryRepository.findRegion2PopularPlaces(
                "서울특별시", "강남구", 37.5283, 126.9326
        )).thenReturn(List.of());
        when(popularPlaceQueryRepository.findRegion1PopularPlaces(
                "서울특별시", 37.5283, 126.9326
        )).thenReturn(region1Candidates);
        when(pinQueryService.findRepresentativePlaceTracksByPlaceIds(
                List.of(21L, 22L, 23L, 24L, 25L, 26L)
        )).thenReturn(Map.of());

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                PopularPlaceScope.GLOBAL, 37.5283, 126.9326
        );

        assertThat(result.scopeLevel()).isEqualTo(PopularPlaceScopeLevel.REGION1);
        assertThat(result.scopeName()).isEqualTo("서울특별시");
        assertThat(result.items()).hasSize(6);
    }

    @Test
    void 행정동_변환_결과와_전국_결과가_모두_없으면_GLOBAL_빈_목록을_반환한다() {
        when(placeLocationMetadataService.getAdministrativeRegion(37.5283, 126.9326))
                .thenReturn(new PlaceAdministrativeRegion(null, null, null, null));
        when(popularPlaceQueryRepository.findGlobalPopularPlaces(37.5283, 126.9326))
                .thenReturn(List.of());

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                PopularPlaceScope.GLOBAL, 37.5283, 126.9326
        );

        assertThat(result.scopeLevel()).isEqualTo(PopularPlaceScopeLevel.GLOBAL);
        assertThat(result.scopeName()).isEqualTo("전국");
        assertThat(result.items()).isEmpty();
        verify(popularPlaceQueryRepository)
                .findGlobalPopularPlaces(37.5283, 126.9326);
        verifyNoInteractions(pinQueryService);
    }

    @Test
    void 모든_행정구역_단계가_5개이면_누적하지_않고_전국_결과만_반환한다() {
        when(placeLocationMetadataService.getAdministrativeRegion(37.5283, 126.9326))
                .thenReturn(region());
        when(popularPlaceQueryRepository.findRegion3PopularPlaces(
                "1168010100", 37.5283, 126.9326
        )).thenReturn(popularCandidates(1L).subList(0, 5));
        when(popularPlaceQueryRepository.findRegion2PopularPlaces(
                "서울특별시", "강남구", 37.5283, 126.9326
        )).thenReturn(popularCandidates(11L).subList(0, 5));
        when(popularPlaceQueryRepository.findRegion1PopularPlaces(
                "서울특별시", 37.5283, 126.9326
        )).thenReturn(popularCandidates(21L).subList(0, 5));
        when(popularPlaceQueryRepository.findGlobalPopularPlaces(37.5283, 126.9326))
                .thenReturn(List.of(new PopularPlaceCandidate(99L, "전국 결과", 10.2, 9L)));
        when(pinQueryService.findRepresentativePlaceTracksByPlaceIds(List.of(99L)))
                .thenReturn(Map.of());

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                PopularPlaceScope.GLOBAL, 37.5283, 126.9326
        );

        assertThat(result.scopeLevel()).isEqualTo(PopularPlaceScopeLevel.GLOBAL);
        assertThat(result.scopeName()).isEqualTo("전국");
        assertThat(result.items()).extracting(PlaceResponse.PopularListItem::placeId)
                .containsExactly(99L);
        verify(pinQueryService).findRepresentativePlaceTracksByPlaceIds(List.of(99L));
    }

    @Test
    void 누락된_행정구역_단계는_건너뛴다() {
        List<PopularPlaceCandidate> region1Candidates = popularCandidates(31L);
        when(placeLocationMetadataService.getAdministrativeRegion(37.5283, 126.9326))
                .thenReturn(new PlaceAdministrativeRegion(
                        null,
                        "서울특별시",
                        null,
                        null
                ));
        when(popularPlaceQueryRepository.findRegion1PopularPlaces(
                "서울특별시", 37.5283, 126.9326
        )).thenReturn(region1Candidates);
        when(pinQueryService.findRepresentativePlaceTracksByPlaceIds(
                List.of(31L, 32L, 33L, 34L, 35L, 36L)
        )).thenReturn(Map.of());

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                PopularPlaceScope.GLOBAL, 37.5283, 126.9326
        );

        assertThat(result.scopeLevel()).isEqualTo(PopularPlaceScopeLevel.REGION1);
        assertThat(result.scopeName()).isEqualTo("서울특별시");
        verify(popularPlaceQueryRepository).findRegion1PopularPlaces(
                "서울특별시", 37.5283, 126.9326
        );
        verifyNoMoreInteractions(popularPlaceQueryRepository);
    }

    @Test
    void Kakao_행정구역_502_예외를_그대로_전파한다() {
        PlaceException exception = new PlaceException(
                PlaceErrorCode.PLACE_EXTERNAL_API_ERROR
        );
        when(placeLocationMetadataService.getAdministrativeRegion(37.5283, 126.9326))
                .thenThrow(exception);

        assertThatThrownBy(() -> placeQueryService.getPopularPlaces(
                PopularPlaceScope.GLOBAL, 37.5283, 126.9326
        )).isSameAs(exception);

        verifyNoInteractions(popularPlaceQueryRepository, pinQueryService);
    }

    @Test
    void Kakao_행정구역_504_예외를_그대로_전파한다() {
        PlaceException exception = new PlaceException(
                PlaceErrorCode.PLACE_EXTERNAL_API_TIMEOUT
        );
        when(placeLocationMetadataService.getAdministrativeRegion(37.5283, 126.9326))
                .thenThrow(exception);

        assertThatThrownBy(() -> placeQueryService.getPopularPlaces(
                PopularPlaceScope.GLOBAL, 37.5283, 126.9326
        )).isSameAs(exception);

        verifyNoInteractions(popularPlaceQueryRepository, pinQueryService);
    }

    @Test
    void 인기_장소가_없으면_빈_목록을_반환하고_대표_이미지를_조회하지_않는다() {
        when(popularPlaceQueryRepository.findNearbyPopularPlaces(37.5283, 126.9326))
                .thenReturn(List.of());

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                PopularPlaceScope.NEARBY,
                37.5283,
                126.9326
        );

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(pinQueryService);
    }

    @Test
    void 저장한_장소에_PIN_정보를_한_번의_배치_조회로_병합한다() {
        List<NearbyBookmarkedPlace> bookmarks = List.of(
                new NearbyBookmarkedPlace(1L, "가까운 장소", 120),
                new NearbyBookmarkedPlace(2L, "PIN 없는 장소", 250)
        );
        when(placeBookmarkQueryRepository.findNearbyActiveBookmarks(
                10L,
                37.5283,
                126.9326
        )).thenReturn(bookmarks);
        when(pinQueryService.findPinInfosByPlaceIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, new PlacePinInfo(true, "최초작성자", 2L)));

        PlaceResponse.BookmarkListResult result = placeQueryService.getPlaceBookmarks(
                10L,
                37.5283,
                126.9326
        );

        assertThat(result.items()).containsExactly(
                new PlaceResponse.BookmarkListItem(1L, "가까운 장소", "최초작성자", 120),
                new PlaceResponse.BookmarkListItem(2L, "PIN 없는 장소", null, 250)
        );
        verify(pinQueryService).findPinInfosByPlaceIds(List.of(1L, 2L));
    }

    @Test
    void 저장한_장소가_없으면_빈_목록을_반환하고_PIN을_조회하지_않는다() {
        when(placeBookmarkQueryRepository.findNearbyActiveBookmarks(
                10L,
                37.5283,
                126.9326
        )).thenReturn(List.of());

        PlaceResponse.BookmarkListResult result = placeQueryService.getPlaceBookmarks(
                10L,
                37.5283,
                126.9326
        );

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(pinQueryService);
    }

    @Test
    void 장소_상세_조회에_기본_정보와_PIN_북마크_상태를_병합한다() {
        Place place = place(1L, 37.5283, 126.9326);
        when(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(place));
        when(pinQueryService.findPinInfosByPlaceIds(List.of(1L)))
                .thenReturn(Map.of(1L, new PlacePinInfo(true, "홍길동", 3L)));
        when(placeBookmarkRepository.existsById(new PlaceBookmarkId(1L, 7L)))
                .thenReturn(true);
        when(pinQueryService.existsActivePinByPlaceIdAndMemberId(1L, 7L))
                .thenReturn(true);

        PlaceResponse.Detail result = placeQueryService.getPlaceDetail(
                7L,
                1L,
                37.5283,
                126.9326
        );

        assertThat(result).isEqualTo(new PlaceResponse.Detail(
                1L,
                "한강",
                "공원",
                "서울특별시 영등포구 여의도동",
                "서울특별시 영등포구 여의동로",
                37.5283,
                126.9326,
                0,
                true,
                true,
                "홍길동",
                3L,
                true,
                true
        ));
        verify(pinQueryService).findPinInfosByPlaceIds(List.of(1L));
        verify(pinQueryService).existsActivePinByPlaceIdAndMemberId(1L, 7L);
        verify(placeBookmarkRepository).existsById(new PlaceBookmarkId(1L, 7L));
        verify(placeRepository, never()).save(any());
    }

    @Test
    void PIN_집계가_없으면_hasPin_false와_pinCount_0을_반환한다() {
        Place place = place(1L, 37.5283, 126.9326);
        when(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(place));
        when(pinQueryService.findPinInfosByPlaceIds(List.of(1L))).thenReturn(Map.of());

        PlaceResponse.Detail result = placeQueryService.getPlaceDetail(
                7L,
                1L,
                37.5283,
                126.9326
        );

        assertThat(result.hasPin()).isFalse();
        assertThat(result.firstPinCreatorNickname()).isNull();
        assertThat(result.pinCount()).isZero();
        assertThat(result.bookmarkedByMe()).isFalse();
        assertThat(result.pinnedByMe()).isFalse();
    }

    @Test
    void 거리는_미터_단위로_반올림한다() {
        Place place = place(1L, latitudeOffset(470.4), 0.0);
        when(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(place));
        when(pinQueryService.findPinInfosByPlaceIds(List.of(1L))).thenReturn(Map.of());

        PlaceResponse.Detail result = placeQueryService.getPlaceDetail(7L, 1L, 0.0, 0.0);

        assertThat(result.distanceMeters()).isEqualTo(470);
        assertThat(result.withinAccessRange()).isTrue();
    }

    @Test
    void 정확히_500미터이면_접근_반경_이내이다() {
        Place place = place(1L, latitudeOffset(500.0), 0.0);
        when(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(place));
        when(pinQueryService.findPinInfosByPlaceIds(List.of(1L))).thenReturn(Map.of());

        PlaceResponse.Detail result = placeQueryService.getPlaceDetail(7L, 1L, 0.0, 0.0);

        assertThat(result.distanceMeters()).isEqualTo(500);
        assertThat(result.withinAccessRange()).isTrue();
    }

    @Test
    void 거리가_500미터를_초과해도_상세를_반환하고_접근_반경_밖으로_표시한다() {
        Place place = place(1L, latitudeOffset(500.1), 0.0);
        when(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(place));
        when(pinQueryService.findPinInfosByPlaceIds(List.of(1L))).thenReturn(Map.of());

        PlaceResponse.Detail result = placeQueryService.getPlaceDetail(7L, 1L, 0.0, 0.0);

        assertThat(result.distanceMeters()).isEqualTo(500);
        assertThat(result.withinAccessRange()).isFalse();
    }

    @Test
    void 존재하지_않거나_삭제된_장소는_PLACE_NOT_FOUND를_던진다() {
        when(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertPlaceError(
                () -> placeQueryService.getPlaceDetail(7L, 1L, 37.5283, 126.9326),
                PlaceErrorCode.PLACE_NOT_FOUND
        );
        verifyNoInteractions(pinQueryService, placeBookmarkRepository);
    }

    @Test
    void 카카오_장소와_활성_Place의_PIN_정보를_병합한다() {
        PlaceRequest.Search request = new PlaceRequest.Search(" 한강 ", 37.5283, 126.9326);
        KakaoPlaceSearchResponse response = response("26338954", "470");
        Place place = mock(Place.class);

        when(kakaoPlaceSearchClient.search("한강", 37.5283, 126.9326))
                .thenReturn(response);
        when(placeRepository.findAllByPlaceProviderAndProviderPlaceIdInAndDeletedAtIsNull(
                "KAKAO",
                List.of("26338954")
        )).thenReturn(List.of(place));
        when(place.getId()).thenReturn(12L);
        when(place.getProviderPlaceId()).thenReturn("26338954");
        when(pinQueryService.findPinInfosByPlaceIds(List.of(12L)))
                .thenReturn(Map.of(12L, new PlacePinInfo(true, "홍길동",1L)));

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(request);

        assertThat(result.items()).containsExactly(new PlaceResponse.SearchItem(
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
        ));
        verify(pinQueryService).findPinInfosByPlaceIds(List.of(12L));
        verify(placeRepository, never()).save(any());
    }

    @Test
    void 주소_검색_결과가_있으면_ADDRESS로_매핑하고_키워드와_PIN_검색을_호출하지_않는다() {
        PlaceRequest.Search request = new PlaceRequest.Search(
                "서울 영등포구 여의도동 84",
                37.5251,
                126.9298
        );
        when(kakaoAddressSearchClient.search("서울 영등포구 여의도동 84"))
                .thenReturn(addressResponse(
                        "서울특별시 영등포구 여의도동 84",
                        "서울특별시 영등포구 여의동로 330",
                        "126.9326",
                        "37.5283"
                ));

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(request);

        PlaceResponse.SearchItem item = result.items().getFirst();
        assertThat(item.resultType()).isEqualTo("ADDRESS");
        assertThat(item.provider()).isEqualTo("KAKAO");
        assertThat(item.providerPlaceId()).isNull();
        assertThat(item.placeName()).isEqualTo("서울특별시 영등포구 여의동로 330");
        assertThat(item.category()).isNull();
        assertThat(item.address()).isEqualTo("서울특별시 영등포구 여의도동 84");
        assertThat(item.roadAddress()).isEqualTo("서울특별시 영등포구 여의동로 330");
        assertThat(item.latitude()).isEqualTo(37.5283);
        assertThat(item.longitude()).isEqualTo(126.9326);
        assertThat(item.distanceMeters()).isEqualTo(433);
        assertThat(item.hasPin()).isFalse();
        assertThat(item.firstPinCreatorNickname()).isNull();
        verifyNoInteractions(kakaoPlaceSearchClient, placeRepository, pinQueryService);
    }

    @Test
    void 도로명_주소가_없으면_지번_주소를_placeName으로_사용한다() {
        when(kakaoAddressSearchClient.search("여의도동 84"))
                .thenReturn(addressResponse(
                        "서울특별시 영등포구 여의도동 84",
                        null,
                        "126.9326",
                        "37.5283"
                ));

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(
                new PlaceRequest.Search("여의도동 84", 37.5283, 126.9326)
        );

        assertThat(result.items().getFirst().placeName())
                .isEqualTo("서울특별시 영등포구 여의도동 84");
        assertThat(result.items().getFirst().roadAddress()).isNull();
        assertThat(result.items().getFirst().distanceMeters()).isZero();
    }

    @Test
    void 중첩_address가_없으면_document_addressName을_사용한다() {
        KakaoAddressSearchResponse.Document document =
                new KakaoAddressSearchResponse.Document(
                        "서울특별시 영등포구 여의도동 84",
                        "126.9326",
                        "37.5283",
                        null,
                        null
                );
        when(kakaoAddressSearchClient.search("여의도동 84"))
                .thenReturn(new KakaoAddressSearchResponse(List.of(document)));

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(
                new PlaceRequest.Search("여의도동 84", 37.5283, 126.9326)
        );

        assertThat(result.items().getFirst().address())
                .isEqualTo("서울특별시 영등포구 여의도동 84");
    }

    @Test
    void 주소_검색_결과는_최대_15개만_반환한다() {
        KakaoAddressSearchResponse.Document document =
                new KakaoAddressSearchResponse.Document(
                        "서울특별시 영등포구 여의도동 84",
                        "126.9326",
                        "37.5283",
                        null,
                        null
                );
        when(kakaoAddressSearchClient.search("여의도동 84"))
                .thenReturn(new KakaoAddressSearchResponse(
                        java.util.Collections.nCopies(16, document)
                ));

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(
                new PlaceRequest.Search("여의도동 84", 37.5283, 126.9326)
        );

        assertThat(result.items()).hasSize(15);
    }

    @Test
    void 주소_결과가_없으면_기존_키워드_검색을_수행한다() {
        when(kakaoPlaceSearchClient.search("한강", 37.5283, 126.9326))
                .thenReturn(response("26338954", "470"));
        when(placeRepository.findAllByPlaceProviderAndProviderPlaceIdInAndDeletedAtIsNull(
                "KAKAO",
                List.of("26338954")
        )).thenReturn(List.of());

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(
                new PlaceRequest.Search("한강", 37.5283, 126.9326)
        );

        assertThat(result.items().getFirst().resultType()).isEqualTo("PLACE");
        verify(kakaoPlaceSearchClient).search("한강", 37.5283, 126.9326);
    }

    @Test
    void 키워드_검색_결과는_거리와_무관하게_Kakao_응답_순서를_유지한다() {
        when(kakaoPlaceSearchClient.search("강남역", 37.5283, 126.9326))
                .thenReturn(new KakaoPlaceSearchResponse(List.of(
                        new KakaoPlaceSearchResponse.Document(
                                "station", "강남역", "교통 > 지하철역", "서울 강남구 역삼동",
                                "서울 강남구 강남대로", "127.0", "37.5", "2000"
                        ),
                        new KakaoPlaceSearchResponse.Document(
                                "restaurant", "강남역 음식점", "음식점", "서울 강남구 역삼동",
                                "서울 강남구 테헤란로", "127.1", "37.5", "100"
                        )
                )));
        when(placeRepository.findAllByPlaceProviderAndProviderPlaceIdInAndDeletedAtIsNull(
                "KAKAO", List.of("station", "restaurant")
        )).thenReturn(List.of());

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(
                new PlaceRequest.Search("강남역", 37.5283, 126.9326)
        );

        assertThat(result.items())
                .extracting(PlaceResponse.SearchItem::placeName)
                .containsExactly("강남역", "강남역 음식점");
        assertThat(result.items())
                .extracting(PlaceResponse.SearchItem::distanceMeters)
                .containsExactly(2000, 100);
    }

    @Test
    void 주소와_키워드_결과가_모두_없으면_빈_items를_반환한다() {
        when(kakaoPlaceSearchClient.search("없는 장소", 37.5283, 126.9326))
                .thenReturn(new KakaoPlaceSearchResponse(List.of()));

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(
                new PlaceRequest.Search("없는 장소", 37.5283, 126.9326)
        );

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(placeRepository, pinQueryService);
    }

    @Test
    void 주소_검색_API_오류는_키워드로_fallback하지_않고_502로_변환한다() {
        when(kakaoAddressSearchClient.search("여의도동 84"))
                .thenThrow(new KakaoClientException("failed"));

        assertPlaceError(
                () -> placeQueryService.searchPlaces(
                        new PlaceRequest.Search("여의도동 84", 37.5283, 126.9326)
                ),
                PlaceErrorCode.PLACE_EXTERNAL_API_ERROR
        );
        verifyNoInteractions(kakaoPlaceSearchClient);
    }

    @Test
    void 주소_검색_timeout은_키워드로_fallback하지_않고_504로_변환한다() {
        when(kakaoAddressSearchClient.search("여의도동 84"))
                .thenThrow(new KakaoClientTimeoutException("timed out", new RuntimeException()));

        assertPlaceError(
                () -> placeQueryService.searchPlaces(
                        new PlaceRequest.Search("여의도동 84", 37.5283, 126.9326)
                ),
                PlaceErrorCode.PLACE_EXTERNAL_API_TIMEOUT
        );
        verifyNoInteractions(kakaoPlaceSearchClient);
    }

    @Test
    void 주소_검색의_필수_주소가_없으면_502로_변환하고_fallback하지_않는다() {
        when(kakaoAddressSearchClient.search("여의도동 84"))
                .thenReturn(addressResponse(null, null, "126.9326", "37.5283"));

        assertPlaceError(
                () -> placeQueryService.searchPlaces(
                        new PlaceRequest.Search("여의도동 84", 37.5283, 126.9326)
                ),
                PlaceErrorCode.PLACE_EXTERNAL_API_ERROR
        );
        verifyNoInteractions(kakaoPlaceSearchClient);
    }

    @Test
    void 주소_검색의_좌표가_잘못되면_502로_변환한다() {
        when(kakaoAddressSearchClient.search("여의도동 84"))
                .thenReturn(addressResponse(
                        "서울특별시 영등포구 여의도동 84",
                        null,
                        "not-number",
                        "37.5283"
                ));

        assertPlaceError(
                () -> placeQueryService.searchPlaces(
                        new PlaceRequest.Search("여의도동 84", 37.5283, 126.9326)
                ),
                PlaceErrorCode.PLACE_EXTERNAL_API_ERROR
        );
    }

    @Test
    void 활성_provider_PLACE_SEARCH_장소를_20m_이내에서_조회한다() {
        Place expected = mock(Place.class);
        when(placeQueryRepository.findNearestActiveProviderPlaceSearchWithin(
                37.5283,
                126.9326,
                20.0
        )).thenReturn(Optional.of(expected));

        Optional<Place> result =
                placeQueryService.findNearestActiveProviderPlaceSearchWithin(
                        37.5283,
                        126.9326
                );

        assertThat(result).contains(expected);
    }

    @Test
    void 활성_Place가_없으면_PIN_없음으로_응답한다() {
        PlaceRequest.Search request = new PlaceRequest.Search("한강", 37.5283, 126.9326);
        when(kakaoPlaceSearchClient.search("한강", 37.5283, 126.9326))
                .thenReturn(response("26338954", "470"));
        when(placeRepository.findAllByPlaceProviderAndProviderPlaceIdInAndDeletedAtIsNull(
                "KAKAO",
                List.of("26338954")
        )).thenReturn(List.of());

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(request);

        assertThat(result.items().getFirst().hasPin()).isFalse();
        assertThat(result.items().getFirst().firstPinCreatorNickname()).isNull();
        verifyNoInteractions(pinQueryService);
        verify(placeRepository, never()).save(any());
    }

    @Test
    void 검색_결과가_없으면_빈_items를_반환한다() {
        PlaceRequest.Search request = new PlaceRequest.Search("없는 장소", 37.5283, 126.9326);
        when(kakaoPlaceSearchClient.search("없는 장소", 37.5283, 126.9326))
                .thenReturn(new KakaoPlaceSearchResponse(List.of()));

        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(request);

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(placeRepository, pinQueryService);
    }

    @Test
    void 검색어가_비어_있으면_장소_검색어_필수_예외를_던진다() {
        PlaceRequest.Search request = new PlaceRequest.Search("   ", 37.5283, 126.9326);

        assertPlaceError(
                () -> placeQueryService.searchPlaces(request),
                PlaceErrorCode.PLACE_SEARCH_KEYWORD_REQUIRED
        );
        verifyNoInteractions(kakaoPlaceSearchClient);
    }

    @Test
    void 현재_위치가_없으면_현재_위치_필수_예외를_던진다() {
        PlaceRequest.Search request = new PlaceRequest.Search("한강", null, 126.9326);

        assertPlaceError(
                () -> placeQueryService.searchPlaces(request),
                PlaceErrorCode.PLACE_CURRENT_LOCATION_REQUIRED
        );
        verifyNoInteractions(kakaoPlaceSearchClient);
    }

    @Test
    void 경도가_없으면_현재_위치_필수_예외를_던진다() {
        PlaceRequest.Search request = new PlaceRequest.Search("한강", 37.5283, null);

        assertPlaceError(
                () -> placeQueryService.searchPlaces(request),
                PlaceErrorCode.PLACE_CURRENT_LOCATION_REQUIRED
        );
        verifyNoInteractions(kakaoPlaceSearchClient);
    }

    @Test
    void 카카오_API_오류를_502_장소_예외로_변환한다() {
        PlaceRequest.Search request = new PlaceRequest.Search("한강", 37.5283, 126.9326);
        when(kakaoPlaceSearchClient.search("한강", 37.5283, 126.9326))
                .thenThrow(new KakaoClientException("failed"));

        assertPlaceError(
                () -> placeQueryService.searchPlaces(request),
                PlaceErrorCode.PLACE_EXTERNAL_API_ERROR
        );
    }

    @Test
    void 카카오_timeout을_504_장소_예외로_변환한다() {
        PlaceRequest.Search request = new PlaceRequest.Search("한강", 37.5283, 126.9326);
        when(kakaoPlaceSearchClient.search("한강", 37.5283, 126.9326))
                .thenThrow(new KakaoClientTimeoutException("timed out", new RuntimeException()));

        assertPlaceError(
                () -> placeQueryService.searchPlaces(request),
                PlaceErrorCode.PLACE_EXTERNAL_API_TIMEOUT
        );
    }

    @Test
    void 카카오_거리값이_잘못되면_502_장소_예외로_변환한다() {
        PlaceRequest.Search request = new PlaceRequest.Search("한강", 37.5283, 126.9326);
        when(kakaoPlaceSearchClient.search("한강", 37.5283, 126.9326))
                .thenReturn(response("26338954", "not-number"));

        assertPlaceError(
                () -> placeQueryService.searchPlaces(request),
                PlaceErrorCode.PLACE_EXTERNAL_API_ERROR
        );
    }

    @Test
    void 활성_장소_조회에_성공한다() {
        Long placeId = 1L;
        Place place = mock(Place.class);
        when(placeRepository.findByIdAndDeletedAtIsNull(placeId))
                .thenReturn(Optional.of(place));

        Place result = placeQueryService.getActivePlace(placeId);

        assertThat(result).isSameAs(place);
    }

    @Test
    void 활성_장소가_없으면_예외가_발생한다() {
        Long placeId = 1L;
        when(placeRepository.findByIdAndDeletedAtIsNull(placeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeQueryService.getActivePlace(placeId))
                .isInstanceOfSatisfying(
                        PlaceException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND)
                );
    }

    @Test
    void provider와_providerPlaceId로_활성_장소를_조회한다() {
        Place place = mock(Place.class);
        when(placeRepository.findByPlaceProviderAndProviderPlaceIdAndDeletedAtIsNull(
                "KAKAO",
                "kakao-place-1"
        )).thenReturn(Optional.of(place));

        Optional<Place> result = placeQueryService.findActivePlaceByProviderAndProviderPlaceId(
                "KAKAO",
                "kakao-place-1"
        );

        assertThat(result).contains(place);
        verify(placeRepository).findByPlaceProviderAndProviderPlaceIdAndDeletedAtIsNull(
                "KAKAO",
                "kakao-place-1"
        );
    }

    private KakaoPlaceSearchResponse response(String providerPlaceId, String distance) {
        return new KakaoPlaceSearchResponse(List.of(new KakaoPlaceSearchResponse.Document(
                providerPlaceId,
                "한강",
                "여행 > 관광,명소 > 공원",
                "서울특별시 영등포구 여의도동",
                "서울특별시 영등포구 여의동로",
                "126.9326",
                "37.5283",
                distance
        )));
    }

    private PlaceAdministrativeRegion region() {
        return new PlaceAdministrativeRegion(
                "1168010100",
                "서울특별시",
                "강남구",
                "역삼1동"
        );
    }

    private List<PopularPlaceCandidate> popularCandidates(long firstPlaceId) {
        return LongStream.range(firstPlaceId, firstPlaceId + 6)
                .mapToObj(placeId -> new PopularPlaceCandidate(
                        placeId,
                        "장소 " + placeId,
                        placeId,
                        1L
                ))
                .toList();
    }

    private Place place(Long placeId, double latitude, double longitude) {
        Place place = mock(Place.class);
        Point location = mock(Point.class);
        when(place.getId()).thenReturn(placeId);
        when(place.getName()).thenReturn("한강");
        when(place.getCategory()).thenReturn("공원");
        when(place.getAddress()).thenReturn("서울특별시 영등포구 여의도동");
        when(place.getRoadAddress()).thenReturn("서울특별시 영등포구 여의동로");
        when(place.getLocation()).thenReturn(location);
        when(location.getY()).thenReturn(latitude);
        when(location.getX()).thenReturn(longitude);
        return place;
    }

    private double latitudeOffset(double distanceMeters) {
        return Math.toDegrees(distanceMeters / 6_371_000.0);
    }

    private KakaoAddressSearchResponse addressResponse(
            String address,
            String roadAddress,
            String longitude,
            String latitude
    ) {
        return new KakaoAddressSearchResponse(List.of(
                new KakaoAddressSearchResponse.Document(
                        address,
                        longitude,
                        latitude,
                        address == null
                                ? null
                                : new KakaoAddressSearchResponse.Address(address),
                        roadAddress == null
                                ? null
                                : new KakaoAddressSearchResponse.RoadAddress(roadAddress)
                )
        ));
    }

    private void assertPlaceError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            PlaceErrorCode errorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        PlaceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode)
                );
    }
}
