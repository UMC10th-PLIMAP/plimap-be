package com.example.plimap.global.external.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleAuthClientImplTest {

    private final KeyPair keyPair = Jwts.SIG.ES256.keyPair().build();
    // Secret Manager에 한 줄로 넣은 경우처럼 줄바꿈을 리터럴 "\n"으로 표현한다.
    private final String pem = "-----BEGIN PRIVATE KEY-----\\n"
            + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            + "\\n-----END PRIVATE KEY-----";
    private final AppleAuthProperties properties =
            new AppleAuthProperties("kr.plimap.ios", "TEAMID1234", "KEYID12345", pem);

    @Test
    void client_secret은_p8_키로_서명한_ES256_JWT다() {
        AppleAuthClientImpl client = new AppleAuthClientImpl(RestClient.create(), properties);

        String clientSecret = client.createClientSecret();

        Claims claims = Jwts.parser().verifyWith(keyPair.getPublic()).build()
                .parseSignedClaims(clientSecret).getPayload();
        assertThat(claims.getIssuer()).isEqualTo("TEAMID1234");
        assertThat(claims.getSubject()).isEqualTo("kr.plimap.ios");
        assertThat(claims.getAudience()).containsExactly("https://appleid.apple.com");
    }

    @Test
    void revoke는_form으로_요청하고_애플이_거부하면_false를_반환한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppleAuthClientImpl client = new AppleAuthClientImpl(builder.build(), properties);
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess());
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andRespond(withBadRequest());

        assertThat(client.revoke("refresh-token")).isTrue();
        assertThat(client.revoke("refresh-token")).isFalse();
        server.verify();
    }
}
