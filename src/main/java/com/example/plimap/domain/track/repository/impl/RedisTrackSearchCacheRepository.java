package com.example.plimap.domain.track.repository.impl;

import com.example.plimap.domain.track.dto.TrackSearchCache;
import com.example.plimap.domain.track.repository.TrackSearchCacheRepository;
import com.example.plimap.domain.track.repository.exception.CacheSerializationException;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

@Repository
@RequiredArgsConstructor
public class RedisTrackSearchCacheRepository implements TrackSearchCacheRepository {

    static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "track:search:v3:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<TrackSearchCache> find(String normalizedKeyword, int limit) {
        String value = redisTemplate.opsForValue().get(key(normalizedKeyword, limit));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, TrackSearchCache.class));
        } catch (JacksonException exception) {
            throw new CacheSerializationException("트랙 검색 캐시 역직렬화에 실패했습니다.", exception);
        }
    }

    @Override
    public void save(String normalizedKeyword, int limit, TrackSearchCache searchResult) {
        String value;
        try {
            value = objectMapper.writeValueAsString(searchResult);
        } catch (JacksonException exception) {
            throw new CacheSerializationException("트랙 검색 캐시 직렬화에 실패했습니다.", exception);
        }
        redisTemplate.opsForValue().set(key(normalizedKeyword, limit), value, TTL);
    }

    private String key(String normalizedKeyword, int limit) {
        return KEY_PREFIX + normalizedKeyword + ":" + limit;
    }
}
