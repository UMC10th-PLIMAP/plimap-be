package com.example.plimap.global.external.kakao;

import com.example.plimap.global.external.kakao.dto.KakaoAddressSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class KakaoAddressSearchClientImpl implements KakaoAddressSearchClient {

    private static final String AUTHORIZATION_PREFIX = "KakaoAK ";
    private static final int MAX_RESULTS = 15;

    private final RestClient kakaoLocalRestClient;
    private final KakaoLocalProperties properties;

    @Override
    public KakaoAddressSearchResponse search(String query) {
        try {
            KakaoAddressSearchResponse response = kakaoLocalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/address.json")
                            .queryParam("query", query)
                            .queryParam("size", MAX_RESULTS)
                            .build())
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            AUTHORIZATION_PREFIX + properties.restApiKey()
                    )
                    .retrieve()
                    .body(KakaoAddressSearchResponse.class);

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
