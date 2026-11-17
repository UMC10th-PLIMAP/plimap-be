package com.example.plimap.domain.pin.converter;

import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.pin.dto.Pagination;
import com.example.plimap.domain.pin.dto.PlaceAccessToken;
import com.example.plimap.domain.pin.dto.request.PinRequest;
import com.example.plimap.domain.pin.dto.response.PinResponse;
import com.example.plimap.domain.pin.entity.Pin;
import com.example.plimap.domain.pin.enums.AvailabilityStatus;
import com.example.plimap.domain.track.entity.Track;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class PinConverter {

    public static PinResponse.Summary toSummary(
            Member member,
            Pin pin,
            Long placeId,
            String writerProfileImage
    ) {
        return PinResponse.Summary.builder()
                .pinId(pin.getId())
                .placeId(placeId)
                .writerNickname(member.getDisplayNickname())
                .writerProfileImage(writerProfileImage)
                .introduction(pin.getIntroduction())
                .clipStartMs(pin.getClipStartMs())
                .youtubeVideoId(pin.getPlaceTrack().getTrack().getProviderTrackId())
                .build();
    }

    public static PinResponse.PinAvailability toPinAvailability(
            AvailabilityStatus status,
            boolean registrable,
            Double distanceFromUserMeters,
            Double nearestPinDistanceMeters
    ) {
        return PinResponse.PinAvailability.builder()
                .status(status)
                .registrable(registrable)
                .distanceFromUserMeters(distanceFromUserMeters)
                .nearestPinDistanceMeters(nearestPinDistanceMeters)
                .build();
    }

    public static PinResponse.UpdatedPin toUpdatedPin(
            Pin pin
    ) {
        return PinResponse.UpdatedPin.builder()
                .introduction(pin.getIntroduction())
                .tags(pin.getPinTagList().stream().map(pinTag -> pinTag.getTag().getName()).toList())
                .feedOpen(pin.isFeedPublic())
                .build();
    }

    public static PinResponse.LikeCount toLikeCount(
            Integer likeCount,
            boolean likedByMe
    ) {
        return PinResponse.LikeCount.builder()
                .likeCount(likeCount)
                .likedByMe(likedByMe)
                .build();
    }

    public static <T> Pagination<T> toPagination(
            List<T> data,
            String nextCursor,
            Boolean hasNext,
            Integer pageSize
    ) {
        return Pagination.<T>builder()
                .data(data)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .pageSize(pageSize)
                .build();
    }

    public static PinResponse.MyPin toMyPin(
            Pin pin
    ) {
        return PinResponse.MyPin.builder()
                .pinId(pin.getId())
                .albumImageUrl(pin.getPlaceTrack().getTrack().getAlbumImageUrl())
                .trackTitle(pin.getPlaceTrack().getTrack().getTitle())
                .artist(pin.getPlaceTrack().getTrack().getArtistName())
                .placeName(pin.getPlace().getName())
                .feedOpen(pin.isFeedPublic())
                .introduction(pin.getIntroduction())
                .tags(pin.getPinTagList().stream().map(pinTag -> pinTag.getTag().getName()).toList())
                .staticCreatedAt(parseCreatedAt(pin.getCreatedAt(), Instant.now()))
                .createdAt(pin.getCreatedAt())
                .build();
    }

    public static PinResponse.PinDetail toPinDetail(
            Pin pin,
            Boolean userLike,
            Boolean pinByMe,
            String writerProfileImage
    ) {
        return PinResponse.PinDetail.builder()
                .memberId(pin.getMember().getId())
                .pinId(pin.getId())
                .writerNickname(pin.getMember().getDisplayNickname())
                .writerProfileImage(writerProfileImage)
                .introduction(pin.getIntroduction())
                .tags(pin.getPinTagList().stream().map(pinTag -> pinTag.getTag().getName()).toList())
                .clipStartMs(pin.getClipStartMs())
                .likeCount(pin.getLikeCount())
                .userLike(userLike)
                .staticCreatedAt(parseCreatedAt(pin.getCreatedAt(), Instant.now()))
                .createdAt(pin.getCreatedAt())
                .pinByMe(pinByMe)
                .build();
    }

    public static String parseCreatedAt(
            Instant createdAt,
            Instant now
    ) {
        Duration gap = Duration.between(createdAt, now);
        if (gap.getSeconds() < 60) {
            return "방금";
        }
        if (gap.toMinutes() < 60) {
            return "%d분 전".formatted(gap.toMinutes());
        }
        if (gap.toHours() < 24) {
            return "%d시간 전".formatted(gap.toHours());
        }
        if (gap.toDays() < 30) {
            return "%d일 전".formatted(gap.toDays());
        }

        if (gap.toDays() < 365) {
            return "%d개월 전".formatted(gap.toDays()/30);
        }
        return "%d년 전".formatted(gap.toDays()/365);
    }

    public static PinResponse.PinPreview toPinPreview(
            Pin pin,
            String writerProfileImage,
            Boolean hasBookmarkedPlace
    ) {
        return PinResponse.PinPreview.builder()
                .placeId(pin.getPlace().getId())
                .latitude(pin.getPlace().getLocation().getY())
                .longitude(pin.getPlace().getLocation().getX())
                .writerId(pin.getMember().getId())
                .writerNickname(pin.getMember().getDisplayNickname())
                .writerProfileImage(writerProfileImage)
                .introduction(pin.getIntroduction())
                .albumImageUrl(pin.getPlaceTrack().getTrack().getAlbumImageUrl())
                .youtubeVideoId(pin.getPlaceTrack().getTrack().getProviderTrackId())
                .clipStartMs(pin.getClipStartMs())
                .hasBookmarkedPlace(hasBookmarkedPlace)
                .build();
    }

    public static PinResponse.ClusterAndPin toClusterAndPin(
            List<PinResponse.Cluster> clusters,
            List<PinResponse.PinPreview> pins,
            Integer zoomLevel
    ) {
        return PinResponse.ClusterAndPin.builder()
                .zoomLevel(zoomLevel)
                .clusters(clusters)
                .pins(pins)
                .build();
    }

    public static PinResponse.FriendPin toFriendPin(
            Pin pin,
            String writerProfileImage
    ) {
        return PinResponse.FriendPin.builder()
                .pinId(pin.getId())
                .placeName(pin.getPlace().getName())
                .latitude(pin.getPlace().getLocation().getY())
                .longitude(pin.getPlace().getLocation().getX())
                .writerNickname(pin.getMember().getNickname())
                .writerProfileImage(writerProfileImage)
                .albumImageUrl(pin.getPlaceTrack().getTrack().getAlbumImageUrl())
                .createdAt(pin.getCreatedAt())
                .build();
    }

    public static PinResponse.PlaceAccessToken toPlaceAccessToken(
            Long placeId,
            String token
    ) {
        return PinResponse.PlaceAccessToken.builder()
                .placeAccessToken(token)
                .placeId(placeId)
                .build();
    }
}
