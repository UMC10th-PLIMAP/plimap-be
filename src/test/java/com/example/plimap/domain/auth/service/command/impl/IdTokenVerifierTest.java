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
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void JWKS_서버에_연결할_수_없으면_500이_아니라_AuthException으로_변환된다() {
        // given - 아무도 듣고 있지 않은 포트라 연결 자체가 실패한다
        String unreachableUrl = "http://localhost:1/keys";
        String idToken = signedIdToken(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() + 60_000));

        // when & then
        assertThatThrownBy(() -> verifier.verify(unreachableUrl, ISSUERS, AUDIENCE, idToken))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void JWKS_응답이_올바른_형식이_아니면_500이_아니라_AuthException으로_변환된다() throws Exception {
        // given - provider가 장애 등으로 JWKS 대신 엉뚱한 응답(예: HTML 에러 페이지)을 준 상황
        HttpServer brokenServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        brokenServer.createContext("/keys", exchange -> {
            byte[] body = "<html>service unavailable</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        brokenServer.start();
        try {
            String brokenUrl = "http://localhost:" + brokenServer.getAddress().getPort() + "/keys";
            String idToken = signedIdToken(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() + 60_000));

            // when & then
            assertThatThrownBy(() -> verifier.verify(brokenUrl, ISSUERS, AUDIENCE, idToken))
                    .isInstanceOf(AuthException.class);
        } finally {
            brokenServer.stop(0);
        }
    }

    @Test
    void 캐시에_없는_kid는_JWKS를_한_번_강제로_재조회해서_찾는다() throws Exception {
        // given - provider가 서명 키를 새 kid로 교체한 상황을 흉내낸다.
        // 처음엔 예전 kid만 담긴 JWKS를 주다가, 두 번째 요청부터는 새 kid를 포함해서 준다.
        String rotatedKid = "rotated-kid";
        KeyPair rotatedKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AtomicInteger requestCount = new AtomicInteger();

        HttpServer rotatingServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        rotatingServer.createContext("/keys", exchange -> {
            String responseBody = requestCount.getAndIncrement() == 0
                    ? jwks(KID, keyPair)
                    : jwksWithTwoKeys(KID, keyPair, rotatedKid, rotatedKeyPair);
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        rotatingServer.start();
        try {
            String rotatingUrl = "http://localhost:" + rotatingServer.getAddress().getPort() + "/keys";
            String idToken = Jwts.builder()
                    .setHeaderParam("kid", rotatedKid)
                    .issuer(ISSUER)
                    .setAudience(AUDIENCE)
                    .subject("provider-subject-rotated")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(rotatedKeyPair.getPrivate(), Jwts.SIG.RS256)
                    .compact();

            // when
            Claims claims = verifier.verify(rotatingUrl, ISSUERS, AUDIENCE, idToken);

            // then - 첫 조회에선 못 찾고, 강제 재조회(두 번째 요청)에서 찾아야 한다
            assertThat(claims.getSubject()).isEqualTo("provider-subject-rotated");
            assertThat(requestCount.get()).isEqualTo(2);
        } finally {
            rotatingServer.stop(0);
        }
    }

    @Test
    void 끝까지_없는_kid면_강제_재조회_후에도_검증에_실패한다() {
        // given - 서명 키 자체가 유효하지 않은(우리 JWKS에 영원히 없는) kid로 서명된 토큰
        String idToken = Jwts.builder()
                .setHeaderParam("kid", "never-registered-kid")
                .issuer(ISSUER)
                .setAudience(AUDIENCE)
                .subject("provider-subject-1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // when & then
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
        return jwks(KID, keyPair);
    }

    private String jwks(String kid, KeyPair pair) {
        return "{\"keys\":[" + jwkEntry(kid, pair) + "]}";
    }

    private String jwksWithTwoKeys(String firstKid, KeyPair firstKeyPair, String secondKid, KeyPair secondKeyPair) {
        return "{\"keys\":[" + jwkEntry(firstKid, firstKeyPair) + "," + jwkEntry(secondKid, secondKeyPair) + "]}";
    }

    private String jwkEntry(String kid, KeyPair pair) {
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        String n = encode(publicKey.getModulus());
        String e = encode(publicKey.getPublicExponent());
        return """
                {"kty":"RSA","kid":"%s","use":"sig","alg":"RS256","n":"%s","e":"%s"}
                """.formatted(kid, n, e).strip();
    }

    private String encode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
