package com.example.plimap.global.external.kakao;

import com.example.plimap.global.external.kakao.dto.KakaoPlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class KakaoPlaceSearchClientImpl implements KakaoPlaceSearchClient {

    private static final String AUTHORIZATION_PREFIX = "KakaoAK ";

    private final RestClient kakaoLocalRestClient;
    private final KakaoLocalProperties properties;

    @Override
    public KakaoPlaceSearchResponse search(
            String keyword,
            double latitude,
            double longitude
    ) {
        try {
            KakaoPlaceSearchResponse response = kakaoLocalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", keyword)
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .queryParam("sort", "accuracy")
                            .build())
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            AUTHORIZATION_PREFIX + properties.restApiKey()
                    )
                    .retrieve()
                    .body(KakaoPlaceSearchResponse.class);

            if (response == null) {
                throw new KakaoClientException("Kakao Local API returned an empty response");
            }
            return response;
        } catch (ResourceAccessException exception) {
            throw KakaoClientExceptionTranslator.translate("Kakao Local API request failed", exception);
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw KakaoClientExceptionTranslator.translate("Kakao Local API request failed", exception);
        }
    }
}
