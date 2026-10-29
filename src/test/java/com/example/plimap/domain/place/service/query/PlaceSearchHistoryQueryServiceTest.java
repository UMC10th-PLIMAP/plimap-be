package com.example.plimap.domain.place.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.plimap.domain.pin.dto.PlacePinInfo;
import com.example.plimap.domain.pin.service.query.PinQueryService;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.place.entity.PlaceSearchHistory;
import com.example.plimap.domain.place.repository.PlaceBookmarkRepository;
import com.example.plimap.domain.place.repository.PlaceRepository;
import com.example.plimap.domain.place.repository.PlaceSearchHistoryRepository;
import com.example.plimap.domain.place.repository.query.PlaceQueryRepository;
import com.example.plimap.domain.place.repository.query.PlaceBookmarkQueryRepository;
import com.example.plimap.domain.place.repository.query.PopularPlaceQueryRepository;
import com.example.plimap.domain.place.service.query.impl.PlaceSearchQueryService;
import com.example.plimap.domain.place.service.query.impl.PlaceQueryServiceImpl;
import com.example.plimap.domain.place.service.query.impl.PopularPlaceQueryService;
import com.example.plimap.global.external.kakao.KakaoAddressSearchClient;
import com.example.plimap.global.external.kakao.KakaoPlaceSearchClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceSearchHistoryQueryServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

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
        PopularPlaceQueryService popularPlaceQueryService = new PopularPlaceQueryService(
                popularPlaceQueryRepository,
                placeLocationMetadataService,
                pinQueryService
        );
        PlaceSearchQueryService placeSearchQueryService = new PlaceSearchQueryService(
                placeRepository,
                kakaoAddressSearchClient,
                kakaoPlaceSearchClient,
                pinQueryService
        );
        placeQueryService = new PlaceQueryServiceImpl(
                placeRepository,
                placeBookmarkRepository,
                placeSearchHistoryRepository,
                placeQueryRepository,
                placeBookmarkQueryRepository,
                pinQueryService,
                popularPlaceQueryService,
                placeSearchQueryService
        );
    }

    @Test
    void 활성_Place의_최근_이력을_스냅샷과_PIN_배치_정보로_변환한다() {
        PlaceSearchHistory latest = history(
                31L,
                11L,
                false,
                "첫 번째 스냅샷",
                "공원",
                "첫 번째 지번 주소",
                "첫 번째 도로명 주소",
                37.5283,
                126.9326,
                Instant.parse("2026-07-27T00:00:00Z")
        );
        PlaceSearchHistory deleted = deletedHistory();
        PlaceSearchHistory older = history(
                29L,
                12L,
                false,
                "두 번째 스냅샷",
                null,
                "두 번째 지번 주소",
                null,
                37.5293,
                126.9326,
                Instant.parse("2026-07-25T00:00:00Z")
        );
        when(placeSearchHistoryRepository
                .findTop5ByMemberIdOrderBySelectedAtDescIdDesc(7L))
                .thenReturn(List.of(latest, deleted, older));
        when(pinQueryService.findPinInfosByPlaceIds(List.of(11L, 12L)))
                .thenReturn(Map.of(11L, new PlacePinInfo(true, "홍길동", 2L)));

        PlaceResponse.SearchHistoryResult result =
                placeQueryService.getSearchHistories(7L, 37.5283, 126.9326);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getFirst())
                .extracting(
                        PlaceResponse.SearchHistoryItem::historyId,
                        PlaceResponse.SearchHistoryItem::placeId,
                        PlaceResponse.SearchHistoryItem::placeName,
                        PlaceResponse.SearchHistoryItem::category,
                        PlaceResponse.SearchHistoryItem::address,
                        PlaceResponse.SearchHistoryItem::roadAddress,
                        PlaceResponse.SearchHistoryItem::latitude,
                        PlaceResponse.SearchHistoryItem::longitude,
                        PlaceResponse.SearchHistoryItem::distanceMeters,
                        PlaceResponse.SearchHistoryItem::hasPin,
                        PlaceResponse.SearchHistoryItem::firstPinCreatorNickname,
                        PlaceResponse.SearchHistoryItem::selectedAt
                )
                .containsExactly(
                        31L,
                        11L,
                        "첫 번째 스냅샷",
                        "공원",
                        "첫 번째 지번 주소",
                        "첫 번째 도로명 주소",
                        37.5283,
                        126.9326,
                        0,
                        true,
                        "홍길동",
                        Instant.parse("2026-07-27T00:00:00Z")
                );
        assertThat(result.items().get(1).roadAddress()).isNull();
        assertThat(result.items().get(1).distanceMeters()).isBetween(110, 112);
        assertThat(result.items().get(1).hasPin()).isFalse();
        assertThat(result.items().get(1).firstPinCreatorNickname()).isNull();
        verify(pinQueryService).findPinInfosByPlaceIds(List.of(11L, 12L));
    }

    @Test
    void 조회할_활성_이력이_없으면_빈_목록을_반환하고_PIN을_조회하지_않는다() {
        when(placeSearchHistoryRepository
                .findTop5ByMemberIdOrderBySelectedAtDescIdDesc(7L))
                .thenReturn(List.of());

        PlaceResponse.SearchHistoryResult result =
                placeQueryService.getSearchHistories(7L, 37.5283, 126.9326);

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(pinQueryService);
    }

    private PlaceSearchHistory history(
            Long historyId,
            Long placeId,
            boolean deleted,
            String placeName,
            String category,
            String address,
            String roadAddress,
            double latitude,
            double longitude,
            Instant selectedAt
    ) {
        Place place = mock(Place.class);
        when(place.getId()).thenReturn(placeId);
        when(place.getRoadAddress()).thenReturn(roadAddress);
        when(place.isDeleted()).thenReturn(deleted);

        PlaceSearchHistory history = mock(PlaceSearchHistory.class);
        when(history.getId()).thenReturn(historyId);
        when(history.getPlace()).thenReturn(place);
        when(history.getPlaceName()).thenReturn(placeName);
        when(history.getCategory()).thenReturn(category);
        when(history.getAddress()).thenReturn(address);
        when(history.getLocation()).thenReturn(GEOMETRY_FACTORY.createPoint(
                new Coordinate(longitude, latitude)
        ));
        when(history.getSelectedAt()).thenReturn(selectedAt);
        return history;
    }

    private PlaceSearchHistory deletedHistory() {
        Place place = mock(Place.class);
        when(place.isDeleted()).thenReturn(true);

        PlaceSearchHistory history = mock(PlaceSearchHistory.class);
        when(history.getPlace()).thenReturn(place);
        return history;
    }
}
