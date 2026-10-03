package com.example.plimap.global.external.kakao;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class KakaoUserApiClientImpl implements KakaoUserApiClient {

    private static final String AUTHORIZATION_PREFIX = "Bearer ";

    private final RestClient kakaoUserApiRestClient;

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
}
