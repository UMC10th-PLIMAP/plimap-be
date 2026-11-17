package com.example.plimap.domain.pin.dto.response;

import com.example.plimap.domain.pin.enums.AvailabilityStatus;
import com.example.plimap.domain.pin.enums.ClusterLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

public class PinResponse {
    @Builder
    public record Summary(
            Long pinId,

            Long placeId,

            String writerNickname,

            String writerProfileImage,

            String introduction,

            String  youtubeVideoId,

            Integer clipStartMs
    ) {}

    @Builder
    public record PinAvailability(
            AvailabilityStatus status,

            boolean registrable,

            Double distanceFromUserMeters,

            Double nearestPinDistanceMeters
    ) {}

    @Builder
    public record UpdatedPin(
            String introduction,

            List<String> tags,

            Boolean feedOpen
    ) {}

    @Builder
    public record LikeCount(
            Integer likeCount,
            boolean likedByMe
    ) {}

    @Builder
    public record Feed(
            Long pinId,
            Long placeId,
            String albumImageUrl,
            Double latitude,
            Double longitude,
            String placeName,
            Integer distanceFromUser,
            Long pinCount,
            Instant createdAt
    ) {}

    @Builder
    public record MyPin(
            Long pinId,
            String albumImageUrl,
            String trackTitle,
            String artist,
            String placeName,
            String introduction,
            List<String> tags,
            @Schema(description = "Current feed visibility for the authenticated PIN owner")
            boolean feedOpen,
            String staticCreatedAt,
            Instant createdAt
    ) {}

    @Builder
    public record PinDetail(
            Long memberId,
            Long pinId,
            String writerNickname,
            String writerProfileImage,
            String introduction,
            List<String> tags,
            Integer clipStartMs,
            Integer likeCount,
            Boolean userLike,
            String staticCreatedAt,
            Instant createdAt,
            boolean pinByMe
    ) {}

    @Builder
    public record PinPreview(
            Long placeId,
            Double latitude,
            Double longitude,
            Long writerId,
            String writerNickname,
            String writerProfileImage,
            String introduction,
            String albumImageUrl,
            String youtubeVideoId,
            Integer clipStartMs,
            Boolean hasBookmarkedPlace
    ) {}

    @Builder
    public record Cluster(
            ClusterLevel clusterLevel,
            String regionName,
            Integer precision,
            Double latitude,
            Double longitude,
            Integer placeCount,
            Bound bounds,
            boolean hasBookmarkedPlace
    ) {}

    @Builder
    public record Bound(
            Double southWestLat,
            Double southWestLng,
            Double northEastLat,
            Double northEastLng
    ) {}

    @Builder
    public record ClusterAndPin(
            Integer zoomLevel,
            List<Cluster> clusters,
            List<PinPreview> pins
    ) {}

    @Builder
    public record FriendPin(
            Long pinId,
            String placeName,
            Double latitude,
            Double longitude,
            String writerNickname,
            String writerProfileImage,
            String albumImageUrl,
            Instant createdAt
    ) {}

    @Builder
    public record PlaceAccessToken(
            String placeAccessToken,
            Long placeId
    ) {}
}
