package com.example.plimap.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.plimap.support.PostgisContainerConfiguration;
import com.example.plimap.support.RedisContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgisContainerConfiguration.class, RedisContainerConfiguration.class})
class AppLoginNonceServiceIntegrationTest {

    @Autowired
    private AppLoginNonceService appLoginNonceService;

    @Test
    void 발급한_nonce는_한_번만_소비할_수_있다() {
        String nonce = appLoginNonceService.issue();

        boolean firstConsume = appLoginNonceService.consume(nonce);
        boolean secondConsume = appLoginNonceService.consume(nonce);

        assertThat(firstConsume).isTrue();
        assertThat(secondConsume).isFalse();
    }

    @Test
    void 발급한_적_없는_nonce는_소비할_수_없다() {
        boolean consumed = appLoginNonceService.consume("never-issued-nonce");

        assertThat(consumed).isFalse();
    }

    @Test
    void null이나_빈_nonce는_소비할_수_없다() {
        assertThat(appLoginNonceService.consume(null)).isFalse();
        assertThat(appLoginNonceService.consume("")).isFalse();
        assertThat(appLoginNonceService.consume("  ")).isFalse();
    }
}
