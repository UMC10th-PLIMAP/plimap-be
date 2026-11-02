package com.example.plimap.global.external.kakao;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class KakaoUserApiClientImpl implements KakaoUserApiClient {

    private static final String AUTHORIZATION_PREFIX = "Bearer ";
    private static final String ADMIN_AUTHORIZATION_PREFIX = "KakaoAK ";

    private final RestClient kakaoUserApiRestClient;
    private final KakaoUserApiProperties properties;

    @Override
    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        try {
            KakaoUserInfoResponse response = kakaoUserApiRestClient.get()
                    .uri("/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_PREFIX + accessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);

            if (response == null) {
                throw new KakaoClientException("Kakao User API returned an empty response");
            }
            return response;
        } catch (ResourceAccessException exception) {
            throw KakaoClientExceptionTranslator.translate("Kakao User API request failed", exception);
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw KakaoClientExceptionTranslator.translate("Kakao User API request failed", exception);
        }
    }

    @Override
    public void unlink(String userId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", userId);
        try {
            kakaoUserApiRestClient.post()
                    .uri("/v1/user/unlink")
                    .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION_PREFIX + properties.adminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw KakaoClientExceptionTranslator.translate("Kakao unlink API request failed", exception);
        }
    }
}
