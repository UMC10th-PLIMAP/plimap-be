package com.example.plimap.domain.track.service.query.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.plimap.domain.track.dto.TrackMetadataCache;
import com.example.plimap.domain.track.dto.PlaybackFailureCache;
import com.example.plimap.domain.track.dto.TrackSearchCache;
import com.example.plimap.domain.track.dto.request.TrackRequest;
import com.example.plimap.domain.track.dto.response.TrackResponse;
import com.example.plimap.domain.track.exception.TrackErrorCode;
import com.example.plimap.domain.track.exception.TrackException;
import com.example.plimap.domain.track.enums.YoutubePlaybackFailureType;
import com.example.plimap.domain.track.repository.PlaceTrackRepository;
import com.example.plimap.domain.track.repository.PlaybackFailureCacheRepository;
import com.example.plimap.domain.track.repository.TrackMetadataCacheRepository;
import com.example.plimap.domain.track.repository.TrackRepository;
import com.example.plimap.domain.track.repository.TrackSearchCacheRepository;
import com.example.plimap.domain.track.repository.exception.CacheSerializationException;
import com.example.plimap.global.external.itunes.ItunesClientException;
import com.example.plimap.global.external.itunes.ItunesSearchClient;
import com.example.plimap.global.external.itunes.dto.ItunesSearchResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

class TrackQueryServiceImplTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 장르는_검색과_메타데이터_캐시에_보존하고_프론트_응답에는_추가하지_않는다(boolean cached) {
        // given
        ItunesSearchResponse.Item original = itunesResponse().results().getFirst();
        ItunesSearchResponse response = new ItunesSearchResponse(1, List.of(new ItunesSearchResponse.Item(
                original.trackId(), original.trackName(), original.artistName(), original.collectionName(),
                original.artworkUrl100(), original.previewUrl(), original.trackTimeMillis(), "Pop"
        )));
        TrackSearchCache cache = TrackSearchCache.from(response);
        if (cached) {
            when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.of(cache));
        } else {
            when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(response);
        }
        // when
        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());
        // then
        assertThat(cache.tracks().getFirst().primaryGenreName()).isEqualTo("Pop");
        verify(trackMetadataCacheRepository).save(cache.tracks().getFirst());
        assertThat(result).isEqualTo(searchResult());
        assertThat(new tools.jackson.databind.ObjectMapper().writeValueAsString(result))
                .doesNotContain("primaryGenreName");
        if (cached) {
            verifyNoInteractions(itunesSearchClient);
        } else {
            verify(trackSearchCacheRepository).save(KEYWORD, LIMIT, cache);
        }
    }

    private static final String KEYWORD = "아이유 밤편지";
    private static final int LIMIT = 20;

    private final ItunesSearchClient itunesSearchClient = mock(ItunesSearchClient.class);
    private final TrackMetadataCacheRepository trackMetadataCacheRepository =
            mock(TrackMetadataCacheRepository.class);
    private final TrackSearchCacheRepository trackSearchCacheRepository =
            mock(TrackSearchCacheRepository.class);
    private final PlaybackFailureCacheRepository playbackFailureCacheRepository =
            mock(PlaybackFailureCacheRepository.class);
    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final PlaceTrackRepository placeTrackRepository = mock(PlaceTrackRepository.class);

    private final TrackQueryServiceImpl trackQueryService = new TrackQueryServiceImpl(
            itunesSearchClient,
            trackMetadataCacheRepository,
            trackSearchCacheRepository,
            playbackFailureCacheRepository
    );

    @Test
    void 캐시가_없으면_iTunes_검색_후_메타데이터와_검색_결과를_저장한다() {
        TrackRequest.Search request = request();
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.empty());
        when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(itunesResponse());

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request);

        assertThat(result).isEqualTo(searchResult());
        verify(trackMetadataCacheRepository).save(metadata());
        verify(trackSearchCacheRepository).save(
                KEYWORD,
                LIMIT,
                new TrackSearchCache(List.of(metadata()))
        );
        verifyNoInteractions(trackRepository, placeTrackRepository);
    }

    @Test
    void 동일한_검색_캐시가_있으면_iTunes_API를_호출하지_않고_반환한다() {
        TrackSearchCache cached = new TrackSearchCache(List.of(metadata()));
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.of(cached));

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());

        assertThat(result).isEqualTo(searchResult());
        verify(trackSearchCacheRepository).find(KEYWORD, LIMIT);
        verifyNoMoreInteractions(trackSearchCacheRepository);
        verify(trackMetadataCacheRepository).save(metadata());
        verifyNoInteractions(
                itunesSearchClient,
                trackRepository,
                placeTrackRepository
        );
    }

    @Test
    void 실패_캐시가_존재하면_검색_응답에_isUnavailable_true를_합성한다() {
        TrackSearchCache cached = new TrackSearchCache(List.of(metadata()));
        PlaybackFailureCache failure = PlaybackFailureCache.create(
                123L,
                "BzYnNdJhZQw",
                101,
                YoutubePlaybackFailureType.EMBED_BLOCKED
        );
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.of(cached));
        when(playbackFailureCacheRepository.findAllByItunesTrackIds(List.of(123L)))
                .thenReturn(Map.of(123L, failure));

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());

        assertThat(result.tracks().getFirst().isUnavailable()).isTrue();
        verify(playbackFailureCacheRepository).findAllByItunesTrackIds(List.of(123L));
        verify(trackSearchCacheRepository, never()).save(KEYWORD, LIMIT, cached);
    }

    @Test
    void 실패_캐시가_없으면_검색_응답의_isUnavailable은_false다() {
        TrackSearchCache cached = new TrackSearchCache(List.of(metadata()));
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.of(cached));
        when(playbackFailureCacheRepository.findAllByItunesTrackIds(List.of(123L)))
                .thenReturn(Map.of());

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());

        assertThat(result.tracks().getFirst().isUnavailable()).isFalse();
    }

    @Test
    void Redis_조회_장애가_발생하면_iTunes_API를_호출해_검색한다() {
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(itunesResponse());

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());

        assertThat(result).isEqualTo(searchResult());
        verify(itunesSearchClient).search(KEYWORD, LIMIT);
    }

    @Test
    void 검색_결과_Redis_저장_장애가_발생해도_검색_결과를_반환한다() {
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.empty());
        when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(itunesResponse());
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(trackSearchCacheRepository)
                .save(KEYWORD, LIMIT, new TrackSearchCache(List.of(metadata())));

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());

        assertThat(result).isEqualTo(searchResult());
    }

    @Test
    void 메타데이터_Redis_저장_장애가_발생해도_검색_결과_캐시를_저장하고_반환한다() {
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.empty());
        when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(itunesResponse());
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(trackMetadataCacheRepository)
                .save(metadata());

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());

        assertThat(result).isEqualTo(searchResult());
        verify(trackSearchCacheRepository).save(
                KEYWORD,
                LIMIT,
                new TrackSearchCache(List.of(metadata()))
        );
    }

    @Test
    void Redis_조회_timeout이_발생하면_iTunes_API를_호출해_검색한다() {
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT))
                .thenThrow(new QueryTimeoutException("redis timeout"));
        when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(itunesResponse());

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());

        assertThat(result).isEqualTo(searchResult());
        verify(itunesSearchClient).search(KEYWORD, LIMIT);
    }

    @Test
    void 검색_캐시_역직렬화_오류를_장애_우회로_숨기지_않는다() {
        CacheSerializationException serializationException =
                new CacheSerializationException("invalid cache json", new RuntimeException());
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenThrow(serializationException);

        assertThatThrownBy(() -> trackQueryService.searchTracks(request()))
                .isSameAs(serializationException);

        verifyNoInteractions(itunesSearchClient, trackMetadataCacheRepository);
    }

    @Test
    void 검색_캐시_직렬화_오류를_장애_우회로_숨기지_않는다() {
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.empty());
        when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(itunesResponse());
        CacheSerializationException serializationException =
                new CacheSerializationException("serialization failed", new RuntimeException());
        doThrow(serializationException)
                .when(trackSearchCacheRepository)
                .save(KEYWORD, LIMIT, new TrackSearchCache(List.of(metadata())));

        assertThatThrownBy(() -> trackQueryService.searchTracks(request()))
                .isSameAs(serializationException);
    }

    @Test
    void 메타데이터_직렬화_오류를_장애_우회로_숨기지_않는다() {
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.empty());
        when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(itunesResponse());
        CacheSerializationException serializationException =
                new CacheSerializationException("serialization failed", new RuntimeException());
        doThrow(serializationException)
                .when(trackMetadataCacheRepository)
                .save(metadata());

        assertThatThrownBy(() -> trackQueryService.searchTracks(request()))
                .isSameAs(serializationException);

        verify(trackSearchCacheRepository, never()).save(
                KEYWORD,
                LIMIT,
                new TrackSearchCache(List.of(metadata()))
        );
    }

    @Test
    void 빈_검색_결과도_정상_결과로_캐싱한다() {
        when(trackSearchCacheRepository.find(KEYWORD, LIMIT)).thenReturn(Optional.empty());
        ItunesSearchResponse emptyResponse = new ItunesSearchResponse(0, List.of());
        when(itunesSearchClient.search(KEYWORD, LIMIT)).thenReturn(emptyResponse);

        TrackResponse.TrackSearchResult result = trackQueryService.searchTracks(request());

        assertThat(result.tracks()).isEmpty();
        verify(trackSearchCacheRepository).save(
                KEYWORD,
                LIMIT,
                new TrackSearchCache(List.of())
        );
        verifyNoInteractions(trackMetadataCacheRepository);
    }

    @Test
    void iTunes_클라이언트_오류를_Track_예외로_변환한다() {
        when(trackSearchCacheRepository.find("아이유", LIMIT)).thenReturn(Optional.empty());
        when(itunesSearchClient.search("아이유", LIMIT))
                .thenThrow(new ItunesClientException("request failed"));

        assertThatThrownBy(() ->
                trackQueryService.searchTracks(new TrackRequest.Search("아이유", LIMIT)))
                .isInstanceOfSatisfying(TrackException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TrackErrorCode.TRACK_EXTERNAL_API_ERROR));

        verify(trackMetadataCacheRepository, never()).save(metadata());
        verify(trackSearchCacheRepository, never()).save(
                "아이유",
                LIMIT,
                new TrackSearchCache(List.of(metadata()))
        );
        verifyNoInteractions(trackRepository, placeTrackRepository);
    }

    private TrackRequest.Search request() {
        return new TrackRequest.Search(KEYWORD, LIMIT);
    }

    private ItunesSearchResponse itunesResponse() {
        return new ItunesSearchResponse(1, List.of(new ItunesSearchResponse.Item(
                123L,
                "밤편지",
                "아이유",
                "Palette",
                "https://image.example/cover.jpg",
                "https://audio.example/preview.m4a",
                253000
        )));
    }

    private TrackResponse.TrackSearchResult searchResult() {
        return new TrackResponse.TrackSearchResult(List.of(
                new TrackResponse.TrackSearchItem(
                123L,
                "밤편지",
                "아이유",
                "Palette",
                "https://image.example/cover.jpg",
                "https://audio.example/preview.m4a",
                253000
                )));
    }

    private TrackMetadataCache metadata() {
        return new TrackMetadataCache(
                123L,
                "밤편지",
                "아이유",
                "Palette",
                "https://image.example/cover.jpg",
                "https://audio.example/preview.m4a",
                253000
        );
    }
}
