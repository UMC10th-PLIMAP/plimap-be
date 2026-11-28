package com.example.plimap.global.external.itunes.dto;

import java.util.List;

public record ItunesSearchResponse(
        int resultCount,
        List<Item> results
) {

    public ItunesSearchResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public record Item(
            Long trackId,
            String trackName,
            String artistName,
            String collectionName,
            String artworkUrl100,
            String previewUrl,
            Integer trackTimeMillis,
            String primaryGenreName
    ) {
        public Item(
                Long trackId, String trackName, String artistName, String collectionName,
                String artworkUrl100, String previewUrl, Integer trackTimeMillis
        ) {
            this(trackId, trackName, artistName, collectionName, artworkUrl100,
                    previewUrl, trackTimeMillis, null);
        }
    }
}
