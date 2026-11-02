package com.example.plimap.global.external.apple;

import com.example.plimap.global.external.oidc.OidcTokenResponse;
import com.example.plimap.global.external.oidc.SocialAuthClientException;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@EnableConfigurationProperties(AppleAuthProperties.class)
public class AppleAuthClientImpl implements AppleAuthClient {

    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final AppleAuthProperties properties;

    public AppleAuthClientImpl(RestClient jwksRestClient, AppleAuthProperties properties) {
        this.restClient = jwksRestClient;
        this.properties = properties;
    }

    @Override
    public OidcTokenResponse exchangeCode(String authorizationCode) {
        MultiValueMap<String, String> form = baseForm();
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        try {
            OidcTokenResponse response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OidcTokenResponse.class);
            if (response == null || response.refreshToken() == null) {
                throw new SocialAuthClientException("Apple token API returned no refresh token");
            }
            return response;
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw new SocialAuthClientException("Apple token API request failed", exception);
        }
    }

    @Override
    public boolean revoke(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        try {
            restClient.post()
                    .uri(REVOKE_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException exception) {
            return false;
        } catch (RestClientException exception) {
            throw new SocialAuthClientException("Apple revoke API request failed", exception);
        }
    }

    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", createClientSecret());
        return form;
    }

    // 애플은 고정 client_secret 대신 .p8 키로 서명한 ES256 JWT를 요구한다(최대 6개월 유효).
    // 호출 빈도가 낮아 캐시 없이 요청마다 새로 만든다.
    String createClientSecret() {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(properties.keyId()).and()
                .issuer(properties.teamId())
                .subject(properties.clientId())
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CLIENT_SECRET_TTL)))
                .signWith(parsePrivateKey(properties.privateKey()), Jwts.SIG.ES256)
                .compact();
    }

    // Secret Manager/.env에 PEM을 한 줄로 넣으면 줄바꿈이 리터럴 "\n"으로 들어오므로 함께 제거한다.
    private static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new SocialAuthClientException("Apple private key is not configured");
        }
        String base64 = pem.replace("\\n", "")
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new SocialAuthClientException("Apple private key is invalid", exception);
        }
    }
}
