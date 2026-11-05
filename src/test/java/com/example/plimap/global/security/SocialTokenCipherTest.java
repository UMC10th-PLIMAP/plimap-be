package com.example.plimap.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SocialTokenCipherTest {

    private final SocialTokenCipher cipher =
            new SocialTokenCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void 암호화한_토큰을_복호화하면_원래_값이_나오고_매번_다른_암호문이_만들어진다() {
        String first = cipher.encrypt("refresh-token");
        String second = cipher.encrypt("refresh-token");

        assertThat(cipher.decrypt(first)).isEqualTo("refresh-token");
        assertThat(first).isNotEqualTo(second).doesNotContain("refresh-token");
    }
}
