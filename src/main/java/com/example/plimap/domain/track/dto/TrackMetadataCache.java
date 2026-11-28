package com.example.plimap.domain.track.dto;

import com.example.plimap.domain.track.dto.response.TrackResponse;
import com.example.plimap.global.external.itunes.dto.ItunesSearchResponse;

public record TrackMetadataCache(
        Long itunesTrackId,
        String title,
        String artistName,
        String albumTitle,
        String albumImageUrl,
        String previewUrl,
        Integer durationMs,
        String primaryGenreName
) {
    public TrackMetadataCache(
            Long itunesTrackId, String title, String artistName, String albumTitle,
            String albumImageUrl, String previewUrl, Integer durationMs
    ) {
        this(itunesTrackId, title, artistName, albumTitle, albumImageUrl,
                previewUrl, durationMs, null);
    }

    public static TrackMetadataCache from(ItunesSearchResponse.Item item) {
        return new TrackMetadataCache(
                item.trackId(), item.trackName(), item.artistName(), item.collectionName(),
                item.artworkUrl100(), item.previewUrl(), item.trackTimeMillis(),
                item.primaryGenreName()
        );
    }

    public static TrackMetadataCache from(TrackResponse.TrackSearchItem item) {
        return new TrackMetadataCache(
                item.itunesTrackId(),
                item.trackName(),
                item.artistName(),
                item.albumName(),
                item.artworkUrl(),
                item.previewUrl(),
                item.durationMs()
        );
    }
}
