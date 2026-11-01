package com.example.plimap.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.plimap.support.PostgisContainerConfiguration;
import com.example.plimap.support.RedisContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
    void 발급한_nonce의_해시값은_한_번만_소비할_수_있다() throws Exception {
        // given - 앱의 애플 로그인 라이브러리가 하듯 SHA-256(소문자 hex)으로 해시한 값이 ID 토큰에 담겨 온다
        String hashedNonce = sha256Hex(appLoginNonceService.issue());

        // when
        boolean firstConsume = appLoginNonceService.consume(hashedNonce);
        boolean secondConsume = appLoginNonceService.consume(hashedNonce);

        // then
        assertThat(firstConsume).isTrue();
        assertThat(secondConsume).isFalse();
    }

    @Test
    void 해시하지_않은_원래_nonce로는_소비할_수_없다() {
        String nonce = appLoginNonceService.issue();

        assertThat(appLoginNonceService.consume(nonce)).isFalse();
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

    private static String sha256Hex(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
