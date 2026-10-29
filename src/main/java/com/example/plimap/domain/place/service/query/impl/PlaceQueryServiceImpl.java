package com.example.plimap.domain.place.service.query.impl;

import com.example.plimap.domain.pin.dto.PlacePinInfo;
import com.example.plimap.domain.pin.service.query.PinQueryService;
import com.example.plimap.domain.place.dto.NearbyBookmarkedPlace;
import com.example.plimap.domain.place.dto.request.PlaceRequest;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.place.entity.PlaceBookmarkId;
import com.example.plimap.domain.place.entity.PlaceSearchHistory;
import com.example.plimap.domain.place.enums.PopularPlaceScope;
import com.example.plimap.domain.place.exception.PlaceErrorCode;
import com.example.plimap.domain.place.exception.PlaceException;
import com.example.plimap.domain.place.repository.PlaceBookmarkRepository;
import com.example.plimap.domain.place.repository.PlaceRepository;
import com.example.plimap.domain.place.repository.PlaceSearchHistoryRepository;
import com.example.plimap.domain.place.repository.query.PlaceBookmarkQueryRepository;
import com.example.plimap.domain.place.repository.query.PlaceQueryRepository;
import com.example.plimap.domain.place.service.query.PlaceQueryService;
import com.example.plimap.global.util.GeoDistanceCalculator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Validated
public class PlaceQueryServiceImpl implements PlaceQueryService {

    private static final double ACCESS_RANGE_METERS = 500.0;
    private static final double DISTANCE_COMPARISON_EPSILON_METERS = 1e-6;
    private static final double PROVIDER_PLACE_SEARCH_DISTANCE_METERS = 20.0;
    private static final PlacePinInfo NO_PIN_INFO = new PlacePinInfo(false, null, 0L);

    private final PlaceRepository placeRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final PlaceSearchHistoryRepository placeSearchHistoryRepository;
    private final PlaceQueryRepository placeQueryRepository;
    private final PlaceBookmarkQueryRepository placeBookmarkQueryRepository;
    private final PinQueryService pinQueryService;
    private final PopularPlaceQueryService popularPlaceQueryService;
    private final PlaceSearchQueryService placeSearchQueryService;

    @Override
    public PlaceResponse.PopularListResult getPopularPlaces(
            PopularPlaceScope scope,
            double latitude,
            double longitude
    ) {
        return popularPlaceQueryService.getPopularPlaces(scope, latitude, longitude);
    }

    @Override
    public PlaceResponse.Detail getPlaceDetail(
            Long memberId,
            Long placeId,
            double latitude,
            double longitude
    ) {
        Place place = getActivePlace(placeId);
        PlacePinInfo pinInfo = pinQueryService.findPinInfosByPlaceIds(List.of(placeId))
                .getOrDefault(placeId, NO_PIN_INFO);
        boolean bookmarkedByMe = placeBookmarkRepository.existsById(
                new PlaceBookmarkId(placeId, memberId)
        );
        boolean pinnedByMe = pinQueryService.existsActivePinByPlaceIdAndMemberId(
                placeId,
                memberId
        );
        double distance = GeoDistanceCalculator.calculateMeters(
                latitude,
                longitude,
                place.getLocation().getY(),
                place.getLocation().getX()
        );

        return new PlaceResponse.Detail(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getAddress(),
                place.getRoadAddress(),
                place.getLocation().getY(),
                place.getLocation().getX(),
                roundToMeters(distance),
                distance <= ACCESS_RANGE_METERS + DISTANCE_COMPARISON_EPSILON_METERS,
                pinInfo.hasPin(),
                pinInfo.firstPinCreatorNickname(),
                pinInfo.pinCount(),
                bookmarkedByMe,
                pinnedByMe
        );
    }

    @Override
    public PlaceResponse.BookmarkListResult getPlaceBookmarks(
            Long memberId,
            double latitude,
            double longitude
    ) {
        List<NearbyBookmarkedPlace> bookmarks =
                placeBookmarkQueryRepository.findNearbyActiveBookmarks(
                        memberId,
                        latitude,
                        longitude
                );
        if (bookmarks.isEmpty()) {
            return new PlaceResponse.BookmarkListResult(List.of());
        }

        List<Long> placeIds = bookmarks.stream()
                .map(NearbyBookmarkedPlace::placeId)
                .toList();
        Map<Long, PlacePinInfo> pinInfosByPlaceId =
                pinQueryService.findPinInfosByPlaceIds(placeIds);

        List<PlaceResponse.BookmarkListItem> items = bookmarks.stream()
                .map(bookmark -> new PlaceResponse.BookmarkListItem(
                        bookmark.placeId(),
                        bookmark.placeName(),
                        pinInfosByPlaceId.getOrDefault(
                                bookmark.placeId(),
                                NO_PIN_INFO
                        ).firstPinCreatorNickname(),
                        bookmark.distanceMeters()
                ))
                .toList();
        return new PlaceResponse.BookmarkListResult(items);
    }

    @Override
    public PlaceResponse.SearchResult searchPlaces(PlaceRequest.Search request) {
        return placeSearchQueryService.searchPlaces(request);
    }

    @Override
    public PlaceResponse.SearchHistoryResult getSearchHistories(
            Long memberId,
            double latitude,
            double longitude
    ) {
        List<PlaceSearchHistory> activeHistories = placeSearchHistoryRepository
                .findTop5ByMemberIdOrderBySelectedAtDescIdDesc(memberId)
                .stream()
                .filter(history -> history.getPlace() != null)
                .filter(history -> !history.getPlace().isDeleted())
                .toList();

        if (activeHistories.isEmpty()) {
            return new PlaceResponse.SearchHistoryResult(List.of());
        }

        List<Long> placeIds = activeHistories.stream()
                .map(history -> history.getPlace().getId())
                .distinct()
                .toList();
        Map<Long, PlacePinInfo> pinInfosByPlaceId =
                pinQueryService.findPinInfosByPlaceIds(placeIds);

        List<PlaceResponse.SearchHistoryItem> items = activeHistories.stream()
                .map(history -> toSearchHistoryItem(
                        history,
                        latitude,
                        longitude,
                        pinInfosByPlaceId
                ))
                .toList();
        return new PlaceResponse.SearchHistoryResult(items);
    }

    private PlaceResponse.SearchHistoryItem toSearchHistoryItem(
            PlaceSearchHistory history,
            double userLatitude,
            double userLongitude,
            Map<Long, PlacePinInfo> pinInfosByPlaceId
    ) {
        Place place = history.getPlace();
        PlacePinInfo pinInfo = pinInfosByPlaceId.getOrDefault(place.getId(), NO_PIN_INFO);
        double distance = GeoDistanceCalculator.calculateMeters(
                userLatitude,
                userLongitude,
                history.getLocation().getY(),
                history.getLocation().getX()
        );

        return new PlaceResponse.SearchHistoryItem(
                history.getId(),
                place.getId(),
                history.getPlaceName(),
                history.getCategory(),
                history.getAddress(),
                place.getRoadAddress(),
                history.getLocation().getY(),
                history.getLocation().getX(),
                roundToMeters(distance),
                pinInfo.hasPin(),
                pinInfo.firstPinCreatorNickname(),
                history.getSelectedAt()
        );
    }

    @Override
    public Place getActivePlace(Long placeId) {
        return placeRepository.findByIdAndDeletedAtIsNull(placeId)
                .orElseThrow(() -> new PlaceException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    @Override
    public Optional<Place> findActivePlaceByProviderAndProviderPlaceId(
            String placeProvider,
            String providerPlaceId
    ) {
        return placeRepository.findByPlaceProviderAndProviderPlaceIdAndDeletedAtIsNull(
                placeProvider,
                providerPlaceId
        );
    }

    @Override
    public Optional<Place> findNearestActiveProviderPlaceSearchWithin(
            double latitude,
            double longitude
    ) {
        return placeQueryRepository.findNearestActiveProviderPlaceSearchWithin(
                latitude,
                longitude,
                PROVIDER_PLACE_SEARCH_DISTANCE_METERS
        );
    }

    private int roundToMeters(double distanceMeters) {
        return Math.toIntExact(Math.round(distanceMeters));
    }
}
