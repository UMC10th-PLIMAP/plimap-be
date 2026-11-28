package com.example.plimap.domain.track.service.command.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.plimap.domain.track.dto.SelectedTrackCache;
import com.example.plimap.domain.track.dto.PlaybackFailureCache;
import com.example.plimap.domain.track.dto.TrackMetadataCache;
import com.example.plimap.domain.track.dto.request.TrackRequest;
import com.example.plimap.domain.track.dto.response.TrackResponse;
import com.example.plimap.domain.track.exception.TrackErrorCode;
import com.example.plimap.domain.track.exception.TrackException;
import com.example.plimap.domain.track.enums.YoutubePlaybackFailureType;
import com.example.plimap.domain.track.repository.PlaybackFailureCacheRepository;
import com.example.plimap.domain.track.repository.SelectedTrackCacheRepository;
import com.example.plimap.domain.track.repository.TrackMetadataCacheRepository;
import com.example.plimap.global.external.youtube.YoutubeClientException;
import com.example.plimap.global.external.youtube.YoutubeSearchClient;
import com.example.plimap.global.external.youtube.dto.YoutubeSearchResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;

class TrackPlaybackPreparationServiceImplTest {

    private static final Long ITUNES_TRACK_ID = 123L;
    private static final String QUERY = "아이유 밤편지";

    private final PlaybackFailureCacheRepository playbackFailureCacheRepository =
            mock(PlaybackFailureCacheRepository.class);

    private final SelectedTrackCacheRepository selectedTrackCacheRepository =
            mock(SelectedTrackCacheRepository.class);
    private final TrackMetadataCacheRepository trackMetadataCacheRepository =
            mock(TrackMetadataCacheRepository.class);
    private final YoutubeSearchClient youtubeSearchClient = mock(YoutubeSearchClient.class);
    private final TrackPlaybackPreparationServiceImpl service =
            new TrackPlaybackPreparationServiceImpl(
                    playbackFailureCacheRepository,
                    selectedTrackCacheRepository,
                    trackMetadataCacheRepository,
                    youtubeSearchClient
            );

    @Test
    void selection_캐시가_있으면_외부_API와_metadata_캐시를_조회하지_않는다() {
        when(selectedTrackCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.of(selectedTrack()));

        TrackResponse.PlaybackPreparationResult result = service.prepare(request());

        assertThat(result).isEqualTo(response());
        verify(selectedTrackCacheRepository).findByItunesTrackId(ITUNES_TRACK_ID);
        verifyNoMoreInteractions(selectedTrackCacheRepository);
        verifyNoInteractions(trackMetadataCacheRepository, youtubeSearchClient);
    }

    @Test
    void failure_캐시가_있으면_가장_먼저_재생_불가로_처리한다() {
        PlaybackFailureCache failure = PlaybackFailureCache.create(
                ITUNES_TRACK_ID,
                "abcdefghijk",
                101,
                YoutubePlaybackFailureType.EMBED_BLOCKED
        );
        when(playbackFailureCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.of(failure));

        assertTrackError(
                () -> service.prepare(request()),
                TrackErrorCode.PLAYBACK_UNAVAILABLE
        );

        verify(playbackFailureCacheRepository).findByItunesTrackId(ITUNES_TRACK_ID);
        verifyNoInteractions(
                selectedTrackCacheRepository,
                trackMetadataCacheRepository,
                youtubeSearchClient
        );
    }

    @Test
    void selection_캐시가_없으면_metadata로_YouTube를_검색하고_선택_캐시를_저장한다() {
        when(selectedTrackCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.empty());
        when(trackMetadataCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.of(metadata()));
        when(youtubeSearchClient.search(QUERY, 5))
                .thenReturn(youtubeResponse("abcdefghijk"));

        TrackResponse.PlaybackPreparationResult result = service.prepare(request());

        assertThat(result).isEqualTo(response());
        verify(youtubeSearchClient).search(QUERY, 5);
        verify(selectedTrackCacheRepository).save(selectedTrack());
    }

    @Test
    void metadata_캐시가_없으면_만료_예외를_발생시킨다() {
        when(selectedTrackCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.empty());
        when(trackMetadataCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.empty());

        assertTrackError(
                () -> service.prepare(request()),
                TrackErrorCode.TRACK_METADATA_CACHE_NOT_FOUND
        );

        verifyNoInteractions(youtubeSearchClient);
        verify(selectedTrackCacheRepository, never()).save(selectedTrack());
    }

    @Test
    void YouTube_검색_결과가_비어_있으면_매칭_실패_예외를_발생시킨다() {
        givenMetadata();
        when(youtubeSearchClient.search(QUERY, 5))
                .thenReturn(new YoutubeSearchResponse(List.of()));

        assertTrackError(
                () -> service.prepare(request()),
                TrackErrorCode.YOUTUBE_MATCH_NOT_FOUND
        );

        verify(selectedTrackCacheRepository, never()).save(selectedTrack());
    }

    @Test
    void 모든_YouTube_videoId가_null이거나_blank이면_매칭에_실패한다() {
        givenMetadata();
        when(youtubeSearchClient.search(QUERY, 5)).thenReturn(new YoutubeSearchResponse(List.of(
                new YoutubeSearchResponse.Item(null),
                new YoutubeSearchResponse.Item(new YoutubeSearchResponse.Id(null)),
                new YoutubeSearchResponse.Item(new YoutubeSearchResponse.Id("  "))
        )));

        assertTrackError(
                () -> service.prepare(request()),
                TrackErrorCode.YOUTUBE_MATCH_NOT_FOUND
        );

        verify(selectedTrackCacheRepository, never()).save(selectedTrack());
    }

    @Test
    void 첫_결과가_무효이면_두_번째_유효한_videoId를_선택한다() {
        givenMetadata();
        when(youtubeSearchClient.search(QUERY, 5)).thenReturn(new YoutubeSearchResponse(List.of(
                new YoutubeSearchResponse.Item(new YoutubeSearchResponse.Id(" ")),
                candidate("abcdefghijk", QUERY)
        )));

        TrackResponse.PlaybackPreparationResult result = service.prepare(request());

        assertThat(result.youtubeVideoId()).isEqualTo("abcdefghijk");
        verify(selectedTrackCacheRepository).save(selectedTrack());
    }

    @Test
    void YouTube_외부_API_오류를_Track_예외로_변환한다() {
        givenMetadata();
        when(youtubeSearchClient.search(QUERY, 5))
                .thenThrow(new YoutubeClientException("request failed"));

        assertTrackError(
                () -> service.prepare(request()),
                TrackErrorCode.YOUTUBE_EXTERNAL_API_ERROR
        );

        verify(selectedTrackCacheRepository, never()).save(selectedTrack());
    }

    @Test
    void selection_캐시_저장_실패는_캐시_오류로_처리한다() {
        givenMetadata();
        when(youtubeSearchClient.search(QUERY, 5))
                .thenReturn(youtubeResponse("abcdefghijk"));
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(selectedTrackCacheRepository)
                .save(selectedTrack());

        assertTrackError(
                () -> service.prepare(request()),
                TrackErrorCode.TRACK_CACHE_ERROR
        );
    }

    @Test
    void Redis_조회_장애를_metadata_만료로_처리하지_않는다() {
        when(selectedTrackCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertTrackError(
                () -> service.prepare(request()),
                TrackErrorCode.TRACK_CACHE_ERROR
        );

        verifyNoInteractions(trackMetadataCacheRepository, youtubeSearchClient);
    }

    private void givenMetadata() {
        when(selectedTrackCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.empty());
        when(trackMetadataCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.of(metadata()));
    }

    private void assertTrackError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            TrackErrorCode errorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(TrackException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private TrackRequest.PlaybackPreparation request() {
        return new TrackRequest.PlaybackPreparation(ITUNES_TRACK_ID);
    }

    private TrackMetadataCache metadata() {
        return new TrackMetadataCache(
                ITUNES_TRACK_ID,
                "밤편지",
                "아이유",
                "Palette",
                "https://image.example/cover.jpg",
                "https://audio.example/preview.m4a",
                253000
        );
    }

    private SelectedTrackCache selectedTrack() {
        return new SelectedTrackCache(
                ITUNES_TRACK_ID,
                "abcdefghijk",
                "밤편지",
                "아이유",
                "Palette",
                "https://image.example/cover.jpg",
                "https://audio.example/preview.m4a",
                253000
        );
    }

    private TrackResponse.PlaybackPreparationResult response() {
        return TrackResponse.PlaybackPreparationResult.from(selectedTrack());
    }

    private YoutubeSearchResponse youtubeResponse(String videoId) {
        return new YoutubeSearchResponse(List.of(
                candidate(videoId, QUERY)
        ));
    }

    private YoutubeSearchResponse.Item candidate(String videoId, String title) {
        return new YoutubeSearchResponse.Item(
                new YoutubeSearchResponse.Id(videoId),
                new YoutubeSearchResponse.Snippet(title, "채널")
        );
    }

    private void givenGenre(String genre) {
        TrackMetadataCache original = metadata();
        when(trackMetadataCacheRepository.findByItunesTrackId(ITUNES_TRACK_ID))
                .thenReturn(Optional.of(new TrackMetadataCache(
                        original.itunesTrackId(), original.title(), original.artistName(),
                        original.albumTitle(), original.albumImageUrl(), original.previewUrl(),
                        original.durationMs(), genre
                )));
    }

    @Test
    void 부적합한_첫_후보를_건너뛰고_첫_통과_후보를_추가_검색_없이_선택한다() {
        // given
        givenGenre("Pop");
        when(youtubeSearchClient.search(QUERY, 5)).thenReturn(new YoutubeSearchResponse(List.of(
                candidate("wrong", "다른 아티스트 밤편지"),
                candidate("abcdefghijk", QUERY),
                candidate("later", QUERY)
        )));
        // when
        TrackResponse.PlaybackPreparationResult result = service.prepare(request());
        // then
        assertThat(result.youtubeVideoId()).isEqualTo("abcdefghijk");
        verify(youtubeSearchClient).search(QUERY, 5);
        verifyNoMoreInteractions(youtubeSearchClient);
        verify(selectedTrackCacheRepository).save(selectedTrack());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 빈_결과나_모든_후보_탈락시_장르로_한번만_재검색한다(boolean empty) {
        // given
        givenGenre("Pop");
        when(youtubeSearchClient.search(QUERY, 5)).thenReturn(new YoutubeSearchResponse(
                empty ? List.of() : List.of(candidate("wrong", QUERY + " (Cover)"))
        ));
        when(youtubeSearchClient.search(QUERY + " Pop", 5)).thenReturn(new YoutubeSearchResponse(List.of(
                candidate("wrong-again", QUERY + " [Playlist]"),
                candidate("abcdefghijk", QUERY),
                candidate("later", QUERY)
        )));
        // when
        TrackResponse.PlaybackPreparationResult result = service.prepare(request());
        // then
        assertThat(result.youtubeVideoId()).isEqualTo("abcdefghijk");
        verify(youtubeSearchClient).search(QUERY, 5);
        verify(youtubeSearchClient).search(QUERY + " Pop", 5);
        verifyNoMoreInteractions(youtubeSearchClient);
        verify(selectedTrackCacheRepository).save(selectedTrack());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 이차_검색도_빈_결과나_부적합_후보이면_캐시_변경_없이_매핑에_실패한다(boolean empty) {
        // given
        givenGenre("Pop");
        when(youtubeSearchClient.search(QUERY, 5)).thenReturn(new YoutubeSearchResponse(List.of()));
        when(youtubeSearchClient.search(QUERY + " Pop", 5)).thenReturn(new YoutubeSearchResponse(
                empty ? List.of() : List.of(candidate("wrong", QUERY + " (Remix)"))
        ));
        // when, then
        assertTrackError(() -> service.prepare(request()), TrackErrorCode.YOUTUBE_MATCH_NOT_FOUND);
        verify(youtubeSearchClient).search(QUERY, 5);
        verify(youtubeSearchClient).search(QUERY + " Pop", 5);
        verifyNoMoreInteractions(youtubeSearchClient);
        verifyUnchangedCaches();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 장르가_없으면_부적합_결과에_중복_검색하지_않는다(String genre) {
        // given
        givenGenre(genre);
        when(youtubeSearchClient.search(QUERY, 5))
                .thenReturn(new YoutubeSearchResponse(List.of(candidate("wrong", "다른 곡"))));
        // when, then
        assertTrackError(() -> service.prepare(request()), TrackErrorCode.YOUTUBE_MATCH_NOT_FOUND);
        verify(youtubeSearchClient).search(QUERY, 5);
        verifyNoMoreInteractions(youtubeSearchClient);
        verifyUnchangedCaches();
    }

    @Test
    void 이차_검색_API_실패를_후보_불일치와_구분한다() {
        // given
        givenGenre("Pop");
        when(youtubeSearchClient.search(QUERY, 5)).thenReturn(new YoutubeSearchResponse(List.of()));
        when(youtubeSearchClient.search(QUERY + " Pop", 5))
                .thenThrow(new YoutubeClientException("request failed"));
        // when, then
        assertTrackError(() -> service.prepare(request()), TrackErrorCode.YOUTUBE_EXTERNAL_API_ERROR);
        verify(youtubeSearchClient).search(QUERY, 5);
        verify(youtubeSearchClient).search(QUERY + " Pop", 5);
        verifyNoMoreInteractions(youtubeSearchClient);
        verifyUnchangedCaches();
    }

    private void verifyUnchangedCaches() {
        verify(selectedTrackCacheRepository).findByItunesTrackId(ITUNES_TRACK_ID);
        verify(playbackFailureCacheRepository).findByItunesTrackId(ITUNES_TRACK_ID);
        verify(trackMetadataCacheRepository).findByItunesTrackId(ITUNES_TRACK_ID);
        verifyNoMoreInteractions(selectedTrackCacheRepository, playbackFailureCacheRepository,
                trackMetadataCacheRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Live", "Live Clip"})
    void 라이브_후보를_제외하고_다음_정상_후보를_추가_검색_없이_선택한다(String version) {
        // given
        givenGenre("Pop");
        when(youtubeSearchClient.search(QUERY, 5)).thenReturn(new YoutubeSearchResponse(List.of(
                candidate("live-video", QUERY + " [" + version + "]"),
                candidate("abcdefghijk", QUERY)
        )));
        // when
        TrackResponse.PlaybackPreparationResult result = service.prepare(request());
        // then
        assertThat(result.youtubeVideoId()).isEqualTo("abcdefghijk");
        verify(youtubeSearchClient).search(QUERY, 5);
        verifyNoMoreInteractions(youtubeSearchClient);
        verify(selectedTrackCacheRepository).save(selectedTrack());
    }

    @Test
    void 모든_후보가_라이브이면_장르_Fallback에서도_라이브를_제외한다() {
        // given
        givenGenre("Pop");
        when(youtubeSearchClient.search(QUERY, 5)).thenReturn(new YoutubeSearchResponse(List.of(
                candidate("live-video", QUERY + " (Live)"),
                candidate("live-clip", QUERY + " - Live Clip")
        )));
        when(youtubeSearchClient.search(QUERY + " Pop", 5)).thenReturn(new YoutubeSearchResponse(List.of(
                candidate("another-live", QUERY + " [Live Clip]"),
                candidate("abcdefghijk", QUERY)
        )));
        // when
        TrackResponse.PlaybackPreparationResult result = service.prepare(request());
        // then
        assertThat(result.youtubeVideoId()).isEqualTo("abcdefghijk");
        verify(youtubeSearchClient).search(QUERY, 5);
        verify(youtubeSearchClient).search(QUERY + " Pop", 5);
        verifyNoMoreInteractions(youtubeSearchClient);
        verify(selectedTrackCacheRepository).save(selectedTrack());
    }
}
