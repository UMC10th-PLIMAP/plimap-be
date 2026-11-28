package com.example.plimap.domain.track.dto;

import com.example.plimap.domain.track.dto.response.TrackResponse;
import com.example.plimap.global.external.itunes.dto.ItunesSearchResponse;
import java.util.List;

public record TrackSearchCache(List<TrackMetadataCache> tracks) {

    public TrackSearchCache {
        tracks = List.copyOf(tracks);
    }

    public static TrackSearchCache from(TrackResponse.TrackSearchResult result) {
        return new TrackSearchCache(result.tracks().stream()
                .map(TrackMetadataCache::from)
                .toList());
    }

    public static TrackSearchCache from(ItunesSearchResponse response) {
        return new TrackSearchCache(response.results().stream()
                .map(TrackMetadataCache::from)
                .toList());
    }

    public TrackResponse.TrackSearchResult toResponse() {
        return new TrackResponse.TrackSearchResult(tracks.stream()
                .map(track -> new TrackResponse.TrackSearchItem(
                        track.itunesTrackId(),
                        track.title(),
                        track.artistName(),
                        track.albumTitle(),
                        track.albumImageUrl(),
                        track.previewUrl(),
                        track.durationMs(),
                        false
                ))
                .toList());
    }
}
