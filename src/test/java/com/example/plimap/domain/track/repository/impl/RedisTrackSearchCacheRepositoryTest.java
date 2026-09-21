package com.example.plimap.domain.track.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.plimap.domain.track.dto.TrackMetadataCache;
import com.example.plimap.domain.track.dto.TrackSearchCache;
import com.example.plimap.domain.track.repository.exception.CacheSerializationException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

class RedisTrackSearchCacheRepositoryTest {

    private static final String KEY = "track:search:v3:taylor swift:20";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisTrackSearchCacheRepository repository =
            new RedisTrackSearchCacheRepository(redisTemplate, objectMapper);

    @Test
    void 검색_결과를_정규화된_검색어와_limit_키로_24시간_저장한다() {
        TrackSearchCache searchCache = searchCache();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        repository.save("taylor swift", 20, searchCache);

        verify(valueOperations).set(
                eq(KEY),
                valueCaptor.capture(),
                eq(Duration.ofHours(24))
        );
        TrackSearchCache stored =
                objectMapper.readValue(valueCaptor.getValue(), TrackSearchCache.class);
        assertThat(stored).isEqualTo(searchCache);
    }

    @Test
    void 저장된_검색_결과를_조회한다() {
        TrackSearchCache searchCache = searchCache();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(objectMapper.writeValueAsString(searchCache));

        Optional<TrackSearchCache> result = repository.find("taylor swift", 20);

        assertThat(result).contains(searchCache);
    }

    @Test
    void 저장된_검색_결과가_없으면_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        Optional<TrackSearchCache> result = repository.find("taylor swift", 20);

        assertThat(result).isEmpty();
    }

    @Test
    void 검색_캐시_역직렬화_오류를_DataAccessException으로_변환한다() {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        JacksonException jacksonException = mock(JacksonException.class);
        RedisTrackSearchCacheRepository failingRepository =
                new RedisTrackSearchCacheRepository(redisTemplate, failingObjectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("invalid-json");
        when(failingObjectMapper.readValue("invalid-json", TrackSearchCache.class))
                .thenThrow(jacksonException);

        assertThatThrownBy(() -> failingRepository.find("taylor swift", 20))
                .isInstanceOf(CacheSerializationException.class)
                .hasCause(jacksonException);
    }

    @Test
    void 검색_캐시_직렬화_오류를_DataAccessException으로_변환한다() {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        JacksonException jacksonException = mock(JacksonException.class);
        RedisTrackSearchCacheRepository failingRepository =
                new RedisTrackSearchCacheRepository(redisTemplate, failingObjectMapper);
        when(failingObjectMapper.writeValueAsString(any(TrackSearchCache.class)))
                .thenThrow(jacksonException);

        assertThatThrownBy(() -> failingRepository.save("taylor swift", 20, searchCache()))
                .isInstanceOf(CacheSerializationException.class)
                .hasCause(jacksonException);
    }

    private TrackSearchCache searchCache() {
        return new TrackSearchCache(List.of(new TrackMetadataCache(
                123L,
                "Blank Space",
                "Taylor Swift",
                "1989",
                "https://image.example/cover.jpg",
                "https://audio.example/preview.m4a",
                231000
        )));
    }
}
