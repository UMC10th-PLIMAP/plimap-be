package com.example.plimap.domain.track.service.command.impl;

import com.example.plimap.domain.track.dto.PlaybackFailureCache;
import com.example.plimap.domain.track.dto.SelectedTrackCache;
import com.example.plimap.domain.track.dto.TrackMetadataCache;
import com.example.plimap.domain.track.dto.request.TrackRequest;
import com.example.plimap.domain.track.dto.response.TrackResponse;
import com.example.plimap.domain.track.exception.TrackErrorCode;
import com.example.plimap.domain.track.exception.TrackException;
import com.example.plimap.domain.track.repository.PlaybackFailureCacheRepository;
import com.example.plimap.domain.track.repository.SelectedTrackCacheRepository;
import com.example.plimap.domain.track.repository.TrackMetadataCacheRepository;
import com.example.plimap.domain.track.service.command.TrackPlaybackPreparationService;
import com.example.plimap.domain.track.service.command.YoutubeCandidateValidator;
import com.example.plimap.global.external.youtube.YoutubeClientException;
import com.example.plimap.global.external.youtube.YoutubeSearchClient;
import com.example.plimap.global.external.youtube.dto.YoutubeSearchResponse;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrackPlaybackPreparationServiceImpl
        implements TrackPlaybackPreparationService {

    private static final int MAX_RESULTS = 5;
    private static final String QUERY_SEPARATOR = " ";

    private final PlaybackFailureCacheRepository playbackFailureCacheRepository;
    private final SelectedTrackCacheRepository selectedTrackCacheRepository;
    private final TrackMetadataCacheRepository trackMetadataCacheRepository;
    private final YoutubeSearchClient youtubeSearchClient;

    @Override
    public TrackResponse.PlaybackPreparationResult prepare(
            TrackRequest.PlaybackPreparation request
    ) {
        Objects.requireNonNull(request, "request must not be null");

        if (findPlaybackFailure(request.itunesTrackId()).isPresent()) {
            throw new TrackException(TrackErrorCode.PLAYBACK_UNAVAILABLE);
        }

        Optional<SelectedTrackCache> cachedSelection = findSelection(request.itunesTrackId());
        if (cachedSelection.isPresent()) {
            return TrackResponse.PlaybackPreparationResult.from(cachedSelection.get());
        }

        TrackMetadataCache metadata = findMetadata(request.itunesTrackId());
        String youtubeVideoId = findVideoId(metadata);
        SelectedTrackCache selectedTrack = SelectedTrackCache.from(metadata, youtubeVideoId);
        saveSelection(selectedTrack);

        return TrackResponse.PlaybackPreparationResult.from(selectedTrack);
    }

    private Optional<PlaybackFailureCache> findPlaybackFailure(Long itunesTrackId) {
        try {
            return playbackFailureCacheRepository.findByItunesTrackId(itunesTrackId);
        } catch (DataAccessException exception) {
            throw new TrackException(TrackErrorCode.TRACK_CACHE_ERROR, exception);
        }
    }

    private Optional<SelectedTrackCache> findSelection(Long itunesTrackId) {
        try {
            return selectedTrackCacheRepository.findByItunesTrackId(itunesTrackId);
        } catch (DataAccessException exception) {
            throw new TrackException(TrackErrorCode.TRACK_CACHE_ERROR, exception);
        }
    }

    private TrackMetadataCache findMetadata(Long itunesTrackId) {
        try {
            return trackMetadataCacheRepository.findByItunesTrackId(itunesTrackId)
                    .orElseThrow(() -> new TrackException(
                            TrackErrorCode.TRACK_METADATA_CACHE_NOT_FOUND
                    ));
        } catch (DataAccessException exception) {
            throw new TrackException(TrackErrorCode.TRACK_CACHE_ERROR, exception);
        }
    }

    private String findVideoId(TrackMetadataCache metadata) {
        String query = metadata.artistName() + QUERY_SEPARATOR + metadata.title();
        Optional<String> videoId = selectVideoId(searchYoutube(query), metadata);
        if (videoId.isPresent()) {
            return videoId.get();
        }
        String genre = metadata.primaryGenreName();
        if (genre == null || genre.isBlank()) {
            throw new TrackException(TrackErrorCode.YOUTUBE_MATCH_NOT_FOUND);
        }
        return selectVideoId(searchYoutube(query + QUERY_SEPARATOR + genre.strip()), metadata)
                .orElseThrow(() -> new TrackException(TrackErrorCode.YOUTUBE_MATCH_NOT_FOUND));
    }

    private YoutubeSearchResponse searchYoutube(String query) {
        try {
            return youtubeSearchClient.search(query, MAX_RESULTS);
        } catch (YoutubeClientException exception) {
            throw new TrackException(TrackErrorCode.YOUTUBE_EXTERNAL_API_ERROR, exception);
        }
    }

    private Optional<String> selectVideoId(YoutubeSearchResponse response, TrackMetadataCache metadata) {
        return response.items().stream()
                .filter(candidate -> YoutubeCandidateValidator.matches(metadata, candidate))
                .map(YoutubeSearchResponse.Item::id)
                .map(YoutubeSearchResponse.Id::videoId)
                .findFirst();
    }

    private void saveSelection(SelectedTrackCache selectedTrack) {
        try {
            selectedTrackCacheRepository.save(selectedTrack);
        } catch (DataAccessException exception) {
            throw new TrackException(TrackErrorCode.TRACK_CACHE_ERROR, exception);
        }
    }
}
