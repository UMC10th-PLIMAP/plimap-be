package com.example.plimap.domain.place.service.query;

import com.example.plimap.domain.place.dto.request.PlaceRequest;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.place.enums.PopularPlaceScope;
import jakarta.validation.Valid;
import java.util.Optional;

public interface PlaceQueryService {

    PlaceResponse.PopularListResult getPopularPlaces(
            Long memberId,
            PopularPlaceScope scope,
            double latitude,
            double longitude
    );

    PlaceResponse.Detail getPlaceDetail(
            Long memberId,
            Long placeId,
            double latitude,
            double longitude
    );

    PlaceResponse.BookmarkListResult getPlaceBookmarks(
            Long memberId,
            double latitude,
            double longitude
    );

    PlaceResponse.SearchResult searchPlaces(@Valid PlaceRequest.Search request);

    PlaceResponse.SearchHistoryResult getSearchHistories(
            Long memberId,
            double latitude,
            double longitude
    );

    Place getActivePlace(Long placeId);

    Optional<Place> findActivePlaceByProviderAndProviderPlaceId(
            String placeProvider,
            String providerPlaceId
    );

    Optional<Place> findNearestActiveProviderPlaceSearchWithin(
            double latitude,
            double longitude
    );
}
