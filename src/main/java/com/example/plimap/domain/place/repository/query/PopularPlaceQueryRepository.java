package com.example.plimap.domain.place.repository.query;

import com.example.plimap.domain.place.dto.PopularPlaceCandidate;
import java.util.List;

public interface PopularPlaceQueryRepository {

    List<PopularPlaceCandidate> findNearbyPopularPlaces(
            Long memberId,
            double latitude,
            double longitude
    );

    List<PopularPlaceCandidate> findRegion3PopularPlaces(
            Long memberId,
            String administrativeRegionCode,
            double latitude,
            double longitude
    );

    List<PopularPlaceCandidate> findRegion2PopularPlaces(
            Long memberId,
            String sido,
            String sigungu,
            double latitude,
            double longitude
    );

    List<PopularPlaceCandidate> findRegion1PopularPlaces(
            Long memberId,
            String sido,
            double latitude,
            double longitude
    );

    List<PopularPlaceCandidate> findGlobalPopularPlaces(
            Long memberId,
            double latitude,
            double longitude
    );
}
