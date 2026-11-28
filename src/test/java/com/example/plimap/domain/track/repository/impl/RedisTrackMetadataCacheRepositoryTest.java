package com.example.plimap.domain.track.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.plimap.domain.track.dto.TrackMetadataCache;
import com.example.plimap.domain.track.repository.exception.CacheSerializationException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

class RedisTrackMetadataCacheRepositoryTest {

    @Test
    void 기존_JSON에_장르가_없어도_메타데이터를_읽는다() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("""
                {"itunesTrackId":123,"title":"Song","artistName":"Artist",
                 "albumTitle":"Album","albumImageUrl":null,"previewUrl":null,"durationMs":1000}
                """);
        // when
        TrackMetadataCache result = repository.findByItunesTrackId(123L).orElseThrow();
        // then
        assertThat(result.title()).isEqualTo("Song");
        assertThat(result.primaryGenreName()).isNull();
    }

    @Test
    void 장르를_포함한_메타데이터가_JSON_왕복_변환에서_유지된다() {
        // given
        TrackMetadataCache metadata = new TrackMetadataCache(
                123L, "Song", "Artist", "Album", null, null, 1000, "Electronica"
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        // when
        repository.save(metadata);
        // then
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(KEY), valueCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofHours(24)));
        assertThat(objectMapper.readValue(valueCaptor.getValue(), TrackMetadataCache.class)).isEqualTo(metadata);
    }

    private static final String KEY = "track:metadata:123";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisTrackMetadataCacheRepository repository =
            new RedisTrackMetadataCacheRepository(redisTemplate, objectMapper);

    @Test
    void 곡_메타데이터를_iTunes_ID_키로_24시간_저장한다() {
        TrackMetadataCache metadata = new TrackMetadataCache(
                123L,
                "밤편지",
                "아이유",
                "Palette",
                "https://image.example/cover.jpg",
                "https://audio.example/preview.m4a",
                253000
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        repository.save(metadata);

        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq(KEY),
                valueCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofHours(24))
        );
        TrackMetadataCache stored =
                objectMapper.readValue(valueCaptor.getValue(), TrackMetadataCache.class);
        assertThat(stored).isEqualTo(metadata);
    }

    @Test
    void iTunes_ID로_곡_메타데이터를_조회한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(objectMapper.writeValueAsString(metadata()));

        Optional<TrackMetadataCache> result = repository.findByItunesTrackId(123L);

        assertThat(result).contains(metadata());
    }

    @Test
    void 곡_메타데이터가_없으면_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(repository.findByItunesTrackId(123L)).isEmpty();
    }

    @Test
    void 메타데이터_역직렬화_오류를_DataAccessException으로_변환한다() {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        JacksonException jacksonException = mock(JacksonException.class);
        RedisTrackMetadataCacheRepository failingRepository =
                new RedisTrackMetadataCacheRepository(redisTemplate, failingObjectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("invalid-json");
        when(failingObjectMapper.readValue("invalid-json", TrackMetadataCache.class))
                .thenThrow(jacksonException);

        assertThatThrownBy(() -> failingRepository.findByItunesTrackId(123L))
                .isInstanceOf(CacheSerializationException.class)
                .hasCause(jacksonException);
    }

    @Test
    void 메타데이터_직렬화_오류를_DataAccessException으로_변환한다() {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        JacksonException jacksonException = mock(JacksonException.class);
        RedisTrackMetadataCacheRepository failingRepository =
                new RedisTrackMetadataCacheRepository(redisTemplate, failingObjectMapper);
        when(failingObjectMapper.writeValueAsString(any(TrackMetadataCache.class)))
                .thenThrow(jacksonException);

        assertThatThrownBy(() -> failingRepository.save(metadata()))
                .isInstanceOf(CacheSerializationException.class)
                .hasCause(jacksonException);
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
