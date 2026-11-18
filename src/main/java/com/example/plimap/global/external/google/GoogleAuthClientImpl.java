package com.example.plimap.global.external.google;

import com.example.plimap.global.external.oidc.OidcTokenResponse;
import com.example.plimap.global.external.oidc.SocialAuthClientException;
import com.example.plimap.global.external.oidc.SocialAuthRejectedException;
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
@EnableConfigurationProperties(GoogleAuthProperties.class)
public class GoogleAuthClientImpl implements GoogleAuthClient {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

    private final RestClient restClient;
    private final GoogleAuthProperties properties;

    public GoogleAuthClientImpl(RestClient jwksRestClient, GoogleAuthProperties properties) {
        this.restClient = jwksRestClient;
        this.properties = properties;
    }

    @Override
    public OidcTokenResponse exchangeCode(String serverAuthCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", serverAuthCode);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "authorization_code");
        // 네이티브 SDK가 발급한 serverAuthCode는 redirect_uri 없이 발급되므로 빈 값으로 교환한다.
        form.add("redirect_uri", "");
        try {
            OidcTokenResponse response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OidcTokenResponse.class);
            if (response == null || response.idToken() == null) {
                throw new SocialAuthClientException("Google token API returned no ID token");
            }
            return response;
        } catch (HttpClientErrorException exception) {
            if (SocialAuthRejectedException.isRejection(exception)) {
                throw new SocialAuthRejectedException("Google rejected the authorization code", exception);
            }
            throw new SocialAuthClientException("Google token API request failed", exception);
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw new SocialAuthClientException("Google token API request failed", exception);
        }
    }

    @Override
    public boolean revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        try {
            restClient.post()
                    .uri(REVOKE_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException exception) {
            if (SocialAuthRejectedException.isRejection(exception)) {
                return false;
            }
            throw new SocialAuthClientException("Google revoke API rejected the request", exception);
        } catch (RestClientException exception) {
            throw new SocialAuthClientException("Google revoke API request failed", exception);
        }
    }
}
