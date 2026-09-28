package com.example.plimap.domain.auth.service.command.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.plimap.domain.auth.exception.AuthException;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 실제 Apple/Google JWKS 서버 대신, 로컬에 임시 HTTP 서버를 띄워 같은 형식의 JWKS를 응답하게 하고
// 자체 생성한 RSA 키쌍으로 서명한 토큰을 검증한다. 외부 네트워크 없이 서명 검증 로직 자체를 확인한다.
class IdTokenVerifierTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final Set<String> ISSUERS = Set.of(ISSUER);
    private static final String AUDIENCE = "test-client-id";
    private static final String KID = "test-kid";

    private HttpServer jwksServer;
    private String jwksUrl;
    private IdTokenVerifier verifier;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        jwksServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        jwksServer.createContext("/keys", exchange -> {
            byte[] body = jwks().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwksServer.start();
        jwksUrl = "http://localhost:" + jwksServer.getAddress().getPort() + "/keys";

        verifier = new IdTokenVerifier();
    }

    @AfterEach
    void tearDown() {
        jwksServer.stop(0);
    }

    @Test
    void 유효한_ID_토큰의_서명과_iss_aud를_검증해서_claims를_반환한다() {
        String idToken = signedIdToken(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() + 60_000));

        Claims claims = verifier.verify(jwksUrl, ISSUERS, AUDIENCE, idToken);

        assertThat(claims.getSubject()).isEqualTo("provider-subject-1");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
    }

    @Test
    void 허용된_issuer가_여러_개면_그_중_하나만_일치해도_통과한다() {
        // given - 구글은 iss가 https://accounts.google.com / accounts.google.com 둘 다 올 수 있다
        Set<String> allowedIssuers = Set.of("https://accounts.google.com", "accounts.google.com");
        String idToken = signedIdToken("accounts.google.com", AUDIENCE, new Date(System.currentTimeMillis() + 60_000));

        Claims claims = verifier.verify(jwksUrl, allowedIssuers, AUDIENCE, idToken);

        assertThat(claims.getIssuer()).isEqualTo("accounts.google.com");
    }

    @Test
    void 허용되지_않은_issuer면_검증에_실패한다() {
        String idToken = signedIdToken("https://evil.example.com", AUDIENCE, new Date(System.currentTimeMillis() + 60_000));

        assertThatThrownBy(() -> verifier.verify(jwksUrl, ISSUERS, AUDIENCE, idToken))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void aud가_다르면_검증에_실패한다() {
        String idToken = signedIdToken(ISSUER, "other-client-id", new Date(System.currentTimeMillis() + 60_000));

        assertThatThrownBy(() -> verifier.verify(jwksUrl, ISSUERS, AUDIENCE, idToken))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        String idToken = signedIdToken(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() - 60_000));

        assertThatThrownBy(() -> verifier.verify(jwksUrl, ISSUERS, AUDIENCE, idToken))
                .isInstanceOf(AuthException.class);
    }

    private String signedIdToken(String issuer, String audience, Date expiration) {
        return Jwts.builder()
                .setHeaderParam("kid", KID)
                .issuer(issuer)
                .setAudience(audience)
                .subject("provider-subject-1")
                .claim("email", "user@example.com")
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String jwks() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String n = encode(publicKey.getModulus());
        String e = encode(publicKey.getPublicExponent());
        return """
                {"keys":[{"kty":"RSA","kid":"%s","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
                """.formatted(KID, n, e);
    }

    private String encode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
