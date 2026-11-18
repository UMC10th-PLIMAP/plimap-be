package com.example.plimap.domain.pin.service.query;

import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.pin.dto.Pagination;
import com.example.plimap.domain.pin.dto.PlaceAccessToken;
import com.example.plimap.domain.pin.dto.PlacePinInfo;
import com.example.plimap.domain.pin.dto.ReportedPinInfo;
import com.example.plimap.domain.track.dto.AlbumImage;
import com.example.plimap.domain.pin.dto.request.PinRequest;
import com.example.plimap.domain.pin.dto.response.PinResponse;
import com.example.plimap.domain.pin.entity.Pin;
import com.example.plimap.domain.pin.enums.PinReportFilter;
import com.example.plimap.domain.pin.enums.PinSortType;
import com.example.plimap.domain.place.entity.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PinQueryService {

    Pin getActivePin(Long pinId);

    PinResponse.PinAvailability validatePinAvailability(PinRequest.PinAvailability request);

    Map<Long, PlacePinInfo> findPinInfosByPlaceIds(List<Long> placeIds);

    Pagination<PinResponse.Feed> findFeedListByMemberId(Long memberId, Long viewerId, String cursor, Integer pageSize, PinRequest.UserLocation request);

    Pagination<PinResponse.MyPin> findMyPinList(Long memberId, String cursor, Integer pageSize);

    Boolean validatePlacePinAccessByMember(Member member, Place place);

    Pagination<PinResponse.PinDetail> findPinListByPlaceTrackIdAndSortType(Member member, String cursor, Integer pageSize, PinSortType pinSortType,
                                                                           Long placeTrackId, PinRequest.UserLocation request, String token);

    PinResponse.PinPreview getPinPreview(Long pinId, Long viewerId);

    boolean existsActivePinByPlaceIdAndMemberId(Long placeId, Long memberId);

    Optional<Long> findActivePlaceTrackIdByPlaceIdAndMemberId(Long placeId, Long memberId);

    PinResponse.ClusterAndPin getClusterPinList(PinRequest.Viewport request, Long memberId);

    Pagination<PinResponse.FriendPin> getFriendRecentPinList(Long memberId, String cursor, Integer pageSize);

    Map<Long, AlbumImage> findRepresentativePlaceTracksByPlaceIds(
            List<Long> placeIds, Long viewerId
    );

    List<Long> findAllPinIdsByMemberId(Long memberId);

    List<Long> findPinIdsLikedByMember(Long memberId);

    long countPinsByMemberId(Long memberId);

    Page<ReportedPinInfo> findReportedPins(PinReportFilter filter, Pageable pageable);

    boolean hasValidFeedToken(String token, Long memberId, Long placeId);
}
