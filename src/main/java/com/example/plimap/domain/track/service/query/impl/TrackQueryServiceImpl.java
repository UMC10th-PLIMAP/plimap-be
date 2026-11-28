package com.example.plimap.domain.track.service.query.impl;

import com.example.plimap.domain.track.dto.PlaybackFailureCache;
import com.example.plimap.domain.track.dto.TrackSearchCache;
import com.example.plimap.domain.track.dto.request.TrackRequest;
import com.example.plimap.domain.track.dto.response.TrackResponse;
import com.example.plimap.domain.track.exception.TrackErrorCode;
import com.example.plimap.domain.track.exception.TrackException;
import com.example.plimap.domain.track.repository.PlaybackFailureCacheRepository;
import com.example.plimap.domain.track.repository.TrackMetadataCacheRepository;
import com.example.plimap.domain.track.repository.TrackSearchCacheRepository;
import com.example.plimap.domain.track.service.query.TrackQueryService;
import com.example.plimap.global.external.itunes.ItunesClientException;
import com.example.plimap.global.external.itunes.ItunesSearchClient;
import com.example.plimap.global.external.itunes.dto.ItunesSearchResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrackQueryServiceImpl implements TrackQueryService {

    private final ItunesSearchClient itunesSearchClient;
    private final TrackMetadataCacheRepository trackMetadataCacheRepository;
    private final TrackSearchCacheRepository trackSearchCacheRepository;
    private final PlaybackFailureCacheRepository playbackFailureCacheRepository;

    @Override
    public TrackResponse.TrackSearchResult searchTracks(TrackRequest.Search request) {
        Objects.requireNonNull(request, "request must not be null");

        Optional<TrackSearchCache> cachedResult = findCachedResult(request);
        if (cachedResult.isPresent()) {
            TrackSearchCache searchCache = cachedResult.get();
            saveMetadata(searchCache);
            return combinePlaybackFailures(searchCache.toResponse());
        }

        ItunesSearchResponse itunesResponse;
        try {
            itunesResponse = itunesSearchClient.search(request.keyword(), request.limit());
        } catch (ItunesClientException exception) {
            throw new TrackException(TrackErrorCode.TRACK_EXTERNAL_API_ERROR, exception);
        }
        TrackResponse.TrackSearchResult result =
                TrackResponse.TrackSearchResult.from(itunesResponse);
        TrackSearchCache searchCache = TrackSearchCache.from(itunesResponse);

        saveMetadata(searchCache);
        saveSearchResult(request, searchCache);

        return combinePlaybackFailures(result);
    }

    private TrackResponse.TrackSearchResult combinePlaybackFailures(
            TrackResponse.TrackSearchResult result
    ) {
        List<Long> itunesTrackIds = result.tracks().stream()
                .map(TrackResponse.TrackSearchItem::itunesTrackId)
                .toList();
        if (itunesTrackIds.isEmpty()) {
            return result;
        }

        try {
            Map<Long, PlaybackFailureCache> failures =
                    playbackFailureCacheRepository.findAllByItunesTrackIds(itunesTrackIds);
            Set<Long> unavailableTrackIds = failures == null
                    ? Set.of()
                    : Set.copyOf(failures.keySet());
            return result.withUnavailable(unavailableTrackIds);
        } catch (RedisConnectionFailureException | QueryTimeoutException exception) {
            log.warn("재생 실패 Redis 캐시 조회에 실패하여 실패 상태 없이 반환합니다.", exception);
            return result;
        }
    }

    private Optional<TrackSearchCache> findCachedResult(TrackRequest.Search request) {
        try {
            return trackSearchCacheRepository.find(request.keyword(), request.limit());
        } catch (RedisConnectionFailureException | QueryTimeoutException exception) {
            log.warn("트랙 검색 Redis 캐시 조회에 실패해 iTunes 검색을 수행합니다.", exception);
            return Optional.empty();
        }
    }

    private void saveMetadata(TrackSearchCache searchCache) {
        try {
            searchCache.tracks().forEach(trackMetadataCacheRepository::save);
        } catch (RedisConnectionFailureException | QueryTimeoutException exception) {
            log.warn("트랙 메타데이터 Redis 캐시 저장에 실패했습니다.", exception);
        }
    }

    private void saveSearchResult(
            TrackRequest.Search request,
            TrackSearchCache searchCache
    ) {
        try {
            trackSearchCacheRepository.save(
                    request.keyword(),
                    request.limit(),
                    searchCache
            );
        } catch (RedisConnectionFailureException | QueryTimeoutException exception) {
            log.warn("트랙 검색 Redis 캐시 저장에 실패했습니다.", exception);
        }
    }
}
