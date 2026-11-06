package com.example.plimap.domain.pin.service.query.impl;

import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.pin.converter.PinConverter;
import com.example.plimap.domain.pin.dto.Pagination;
import com.example.plimap.domain.pin.dto.PlaceAccessToken;
import com.example.plimap.domain.pin.dto.PlacePinInfo;
import com.example.plimap.domain.pin.dto.ReportedPinInfo;
import com.example.plimap.domain.pin.repository.PlaceAccessTokenRepository;
import com.example.plimap.domain.pin.repository.query.ClusterAndPinQueryRepository;
import com.example.plimap.domain.track.dto.AlbumImage;
import com.example.plimap.domain.pin.dto.request.PinRequest;
import com.example.plimap.domain.pin.dto.response.PinResponse;
import com.example.plimap.domain.pin.entity.Pin;
import com.example.plimap.domain.pin.enums.AvailabilityStatus;
import com.example.plimap.domain.pin.enums.PinReportFilter;
import com.example.plimap.domain.pin.enums.PinSortType;
import com.example.plimap.domain.pin.exception.PinErrorCode;
import com.example.plimap.domain.pin.exception.PinException;
import com.example.plimap.domain.pin.repository.PinLikeRepository;
import com.example.plimap.domain.pin.repository.PinRepository;
import com.example.plimap.domain.pin.repository.query.PinQueryRepository;
import com.example.plimap.domain.pin.service.query.PinQueryService;
import com.example.plimap.domain.pin.validator.PinLocationValidator;
import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.track.entity.PlaceTrack;
import com.example.plimap.domain.track.service.query.PlaceTrackFinder;
import com.example.plimap.domain.track.service.query.PlaceTrackLikeQueryService;
import com.example.plimap.global.external.storage.ProfileImageStorage;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinQueryServiceImpl implements PinQueryService {

    private final PinLocationValidator pinLocationValidator;
    private final PinQueryRepository pinQueryRepository;
    private final PinRepository pinRepository;
    private final PinLikeRepository pinLikeRepository;
    private final ProfileImageStorage profileImageStorage;
    private final PlaceTrackFinder placeTrackFinder;
    private final PlaceAccessTokenRepository placeAccessTokenRepository;
    private final PlaceTrackLikeQueryService placeTrackLikeQueryService;
    private final ClusterAndPinQueryRepository clusterAndPinRepository;
    GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    public Pin getActivePin(Long pinId) {
        return pinRepository.findByIdAndDeletedAtIsNull(pinId)
                .orElseThrow(() -> new PinException(PinErrorCode.PIN_NOT_FOUND));
    }

    @Override
    public PinResponse.PinAvailability validatePinAvailability(PinRequest.PinAvailability request) {
        // 현위치와 장소 사이 거리가 500m 이하인지 검증
        double distanceFromUserMeters = pinLocationValidator.calculateDistance(request.userLatitude(), request.userLongitude(), request.latitude(), request.longitude());
        if (distanceFromUserMeters > 500) {
            return PinConverter.toPinAvailability(AvailabilityStatus.OUT_OF_RANGE, false, distanceFromUserMeters, null);
        }

        // 10m 이내에 PIN 존재하는지 검증
        Double nearestPinDistanceMeters = pinQueryRepository.findNearestActivePinWithin10m(request.latitude(), request.longitude()).orElse(null);
        if (nearestPinDistanceMeters != null) {
            return PinConverter.toPinAvailability(AvailabilityStatus.TOO_CLOSE_TO_PIN, false, distanceFromUserMeters, nearestPinDistanceMeters);
        }

        return PinConverter.toPinAvailability(AvailabilityStatus.CREATABLE_NEW_PLACE, true, distanceFromUserMeters, null);
    }

    @Override
    public Map<Long, PlacePinInfo> findPinInfosByPlaceIds(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return pinQueryRepository.findPinInfosByPlaceIds(placeIds);
    }

    @Override
    public Pagination<PinResponse.Feed> findFeedListByMemberId(Long memberId, Long viewerId, String cursor, Integer pageSize, PinRequest.UserLocation request) {
        return pinQueryRepository.findFeedListByMemberId(memberId, viewerId, cursor, pageSize, request);
    }

    @Override
    public Pagination<PinResponse.MyPin> findMyPinList(Long memberId, String cursor, Integer pageSize) {
        return pinQueryRepository.findMyPinList(memberId, cursor, pageSize);
    }

    @Override
    public Boolean validatePlacePinAccessByMember(Member member, Place place) {
        return pinRepository.existsByMemberAndPlaceAndDeletedAtIsNull(member, place);
    }

    @Override
    public Pagination<PinResponse.PinDetail> findPinListByPlaceTrackIdAndSortType(Member member, String cursor, Integer pageSize, PinSortType pinSortType, Long placeTrackId, PinRequest.UserLocation request, String token) {
        PlaceTrack placeTrack = placeTrackFinder.getActivePlaceTrack(placeTrackId);
        Place place = placeTrack.getPlace();
        double distance = pinLocationValidator.calculateDistance(request.userLatitude(), request.userLongitude(),
                place.getLocation().getY(), place.getLocation().getX());

        boolean hasDistanceAccess = distance <= 500;
        boolean hasMyPinAccess = validatePlacePinAccessByMember(member, place);
        boolean hasLikeAccess = placeTrackLikeQueryService.existsActivePlaceTrackLikedByMemberAtPlace(member.getId(), place.getId());

        if (!hasDistanceAccess && !hasLikeAccess && !hasMyPinAccess) {
            if (!hasValidFeedToken(token, member.getId(), place.getId())) {
                throw new PinException(PinErrorCode.PIN_ACCESS_DENIED);
            }
        }

        return pinQueryRepository.findPinListByPlaceTrackIdAndSortType(member.getId(), cursor, pageSize, pinSortType, placeTrackId);
    }

    @Override
    public PinResponse.PinPreview getPinPreview(Long pinId, Long viewerId) {
        Pin pin = pinQueryRepository.getPinPreview(pinId, viewerId)
                .orElseThrow(() -> new PinException(PinErrorCode.PIN_NOT_FOUND));

        return PinConverter.toPinPreview(pin, profileImageStorage.getPublicUrlOrNull(pin.getMember().getProfileImageObjectKey()), null);
    }

    @Override
    public boolean existsActivePinByPlaceIdAndMemberId(Long placeId, Long memberId) {
        return pinQueryRepository.existsActivePinByPlaceIdAndMemberId(placeId, memberId);
    }

    @Override
    public Optional<Long> findActivePlaceTrackIdByPlaceIdAndMemberId(Long placeId, Long memberId) {
        return pinQueryRepository.findActivePlaceTrackIdByPlaceIdAndMemberId(placeId, memberId);
    }

    @Override
    public PinResponse.ClusterAndPin getClusterPinList(PinRequest.Viewport request, Long memberId) {
        Point minPoint = geometryFactory.createPoint(new Coordinate(request.southWestLng(), request.southWestLat()));
        Point maxPoint = geometryFactory.createPoint(new Coordinate(request.northEastLng(), request.northEastLat()));
        if (request.zoomLevel() >= 20) {
            // 개별 Pin 조회
            List<PinResponse.PinPreview> pinPreviews = clusterAndPinRepository.findPinPreviewListByViewport(minPoint, maxPoint, memberId);
            return PinConverter.toClusterAndPin(null, pinPreviews, request.zoomLevel());
        }

        if (request.zoomLevel() >= 14) {
            // geohash 클러스터 (+개별핀 조회)
            return clusterAndPinRepository.findGeohashClusterListByViewport(minPoint, maxPoint, request.zoomLevel(), getPrecision(request.zoomLevel()), memberId);
        }

        // 행정구역 기반 클러스터 조회
        List<PinResponse.Cluster> clusters = clusterAndPinRepository.findClusterListByViewport(minPoint, maxPoint, request.zoomLevel(), memberId);
        return PinConverter.toClusterAndPin(clusters, null, request.zoomLevel());
    }

    @Override
    public Pagination<PinResponse.FriendPin> getFriendRecentPinList(Long memberId, String cursor, Integer pageSize) {
        return pinQueryRepository.getFriendRecentPinList(memberId, cursor, pageSize);
    }

    @Override
    public Map<Long, AlbumImage> findRepresentativePlaceTracksByPlaceIds(
            List<Long> placeIds, Long viewerId
    ) {
        if (placeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return pinQueryRepository.findRepresentativePlaceTracksByPlaceIds(placeIds, viewerId);
    }

    @Override
    public List<Long> findAllPinIdsByMemberId(Long memberId) {
        return pinRepository.findIdsByMemberId(memberId);
    }

    @Override
    public List<Long> findPinIdsLikedByMember(Long memberId) {
        return pinLikeRepository.findPinIdsByMemberId(memberId);
    }

    @Override
    public long countPinsByMemberId(Long memberId) {
        return pinQueryRepository.countPinsByMemberId(memberId);
    }

    @Override
    public Page<ReportedPinInfo> findReportedPins(PinReportFilter filter, Pageable pageable) {
        return pinQueryRepository.findReportedPins(filter, pageable);
    }

    private Integer getPrecision(Integer zoomLevel) {
        if (zoomLevel <= 16) {
            return 7;
        }
        return 8;
    }

    @Override
    public boolean hasValidFeedToken(String token, Long memberId, Long placeId) {
        Optional<PlaceAccessToken> optional = placeAccessTokenRepository.findByToken(token);

        if (optional.isEmpty()) {
            return false;
        }

        PlaceAccessToken placeAccessToken = optional.get();

        return placeAccessToken.memberId().equals(memberId)
                && placeAccessToken.placeId().equals(placeId);
    }
}
