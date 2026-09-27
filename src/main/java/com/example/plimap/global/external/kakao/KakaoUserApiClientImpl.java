package com.example.plimap.global.external.kakao;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
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
            if (isTimeout(exception)) {
                throw new KakaoClientTimeoutException("Kakao User API request timed out", exception);
            }
            throw new KakaoClientException("Kakao User API request failed", exception);
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw new KakaoClientException("Kakao User API request failed", exception);
        }
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
