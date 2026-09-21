package com.example.plimap.domain.track.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.plimap.domain.track.dto.TrackSearchCache;
import com.example.plimap.domain.track.repository.TrackSearchCacheRepository;
import com.example.plimap.support.PostgisContainerConfiguration;
import com.example.plimap.support.RedisContainerConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgisContainerConfiguration.class, RedisContainerConfiguration.class})
class RedisTrackSearchCacheRepositoryIntegrationTest {

    private static final String TAYLOR_KEY = "track:search:v3:taylor swift:20";
    private static final String IU_KEY = "track:search:v3:아이유:20";
    private static final String IU_LIMIT_KEY = "track:search:v3:아이유:50";

    @Autowired
    private TrackSearchCacheRepository trackSearchCacheRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(List.of(TAYLOR_KEY, IU_KEY, IU_LIMIT_KEY));
    }

    @Test
    void 정규화된_검색어와_limit별_키에_빈_결과도_24시간_저장한다() {
        TrackSearchCache emptyCache = new TrackSearchCache(List.of());

        trackSearchCacheRepository.save("taylor swift", 20, emptyCache);
        trackSearchCacheRepository.save("아이유", 20, emptyCache);
        trackSearchCacheRepository.save("아이유", 50, emptyCache);

        assertThat(trackSearchCacheRepository.find("taylor swift", 20))
                .contains(emptyCache);
        assertThat(redisTemplate.hasKey(TAYLOR_KEY)).isTrue();
        assertThat(redisTemplate.hasKey(IU_KEY)).isTrue();
        assertThat(redisTemplate.hasKey(IU_LIMIT_KEY)).isTrue();
        assertThat(redisTemplate.getExpire(TAYLOR_KEY))
                .isBetween(
                        Duration.ofHours(24).minusSeconds(10).toSeconds(),
                        Duration.ofHours(24).toSeconds()
                );
    }
}
