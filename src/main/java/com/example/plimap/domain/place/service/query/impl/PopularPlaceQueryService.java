package com.example.plimap.domain.place.service.query.impl;

import com.example.plimap.domain.pin.service.query.PinQueryService;
import com.example.plimap.domain.place.dto.PlaceAdministrativeRegion;
import com.example.plimap.domain.place.dto.PopularPlaceCandidate;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.domain.place.enums.PopularPlaceScope;
import com.example.plimap.domain.place.enums.PopularPlaceScopeLevel;
import com.example.plimap.domain.place.repository.query.PopularPlaceQueryRepository;
import com.example.plimap.domain.place.service.query.PlaceLocationMetadataService;
import com.example.plimap.domain.track.dto.AlbumImage;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopularPlaceQueryService {

    private static final int POPULAR_PLACE_THRESHOLD = 6;
    private static final String GLOBAL_SCOPE_NAME = "전국";

    private final PopularPlaceQueryRepository popularPlaceQueryRepository;
    private final PlaceLocationMetadataService placeLocationMetadataService;
    private final PinQueryService pinQueryService;

    public PlaceResponse.PopularListResult getPopularPlaces(
            PopularPlaceScope scope,
            double latitude,
            double longitude
    ) {
        PopularPlaceSelection selection = switch (scope) {
            case NEARBY -> new PopularPlaceSelection(
                    null,
                    null,
                    popularPlaceQueryRepository.findNearbyPopularPlaces(
                            latitude,
                            longitude
                    )
            );
            case GLOBAL -> findPopularPlacesWithRegionalFallback(latitude, longitude);
        };
        List<PopularPlaceCandidate> candidates = selection.candidates();
        if (candidates.isEmpty()) {
            return new PlaceResponse.PopularListResult(
                    selection.scopeLevel(),
                    selection.scopeName(),
                    List.of()
            );
        }

        List<Long> placeIds = candidates.stream()
                .map(PopularPlaceCandidate::placeId)
                .toList();
        Map<Long, AlbumImage> representativeImages =
                pinQueryService.findRepresentativePlaceTracksByPlaceIds(placeIds);

        List<PlaceResponse.PopularListItem> items = candidates.stream()
                .map(candidate -> {
                    AlbumImage albumImage = representativeImages.get(candidate.placeId());
                    return new PlaceResponse.PopularListItem(
                            candidate.placeId(),
                            candidate.placeName(),
                            Math.toIntExact(Math.round(candidate.distanceMeters())),
                            candidate.pinCount(),
                            albumImage == null ? null : albumImage.albumImageUrl()
                    );
                })
                .toList();
        return new PlaceResponse.PopularListResult(
                selection.scopeLevel(),
                selection.scopeName(),
                items
        );
    }

    private PopularPlaceSelection findPopularPlacesWithRegionalFallback(
            double latitude,
            double longitude
    ) {
        PlaceAdministrativeRegion region =
                placeLocationMetadataService.getAdministrativeRegion(latitude, longitude);

        if (region.code() != null && region.eupMyeonDong() != null) {
            List<PopularPlaceCandidate> candidates =
                    popularPlaceQueryRepository.findRegion3PopularPlaces(
                            region.code(),
                            latitude,
                            longitude
                    );
            if (candidates.size() >= POPULAR_PLACE_THRESHOLD) {
                return new PopularPlaceSelection(
                        PopularPlaceScopeLevel.REGION3,
                        region.eupMyeonDong(),
                        candidates
                );
            }
        }

        if (region.sido() != null && region.sigungu() != null) {
            List<PopularPlaceCandidate> candidates =
                    popularPlaceQueryRepository.findRegion2PopularPlaces(
                            region.sido(),
                            region.sigungu(),
                            latitude,
                            longitude
                    );
            if (candidates.size() >= POPULAR_PLACE_THRESHOLD) {
                return new PopularPlaceSelection(
                        PopularPlaceScopeLevel.REGION2,
                        region.sigungu(),
                        candidates
                );
            }
        }

        if (region.sido() != null) {
            List<PopularPlaceCandidate> candidates =
                    popularPlaceQueryRepository.findRegion1PopularPlaces(
                            region.sido(),
                            latitude,
                            longitude
                    );
            if (candidates.size() >= POPULAR_PLACE_THRESHOLD) {
                return new PopularPlaceSelection(
                        PopularPlaceScopeLevel.REGION1,
                        region.sido(),
                        candidates
                );
            }
        }

        return new PopularPlaceSelection(
                PopularPlaceScopeLevel.GLOBAL,
                GLOBAL_SCOPE_NAME,
                popularPlaceQueryRepository.findGlobalPopularPlaces(latitude, longitude)
        );
    }

    private record PopularPlaceSelection(
            PopularPlaceScopeLevel scopeLevel,
            String scopeName,
            List<PopularPlaceCandidate> candidates
    ) {
    }
}
