package com.example.plimap.global.external.youtube;

import com.example.plimap.global.external.youtube.dto.YoutubeSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class YoutubeSearchClientImpl implements YoutubeSearchClient {

    private final RestClient youtubeRestClient;
    private final YoutubeProperties properties;

    @Override
    public YoutubeSearchResponse search(String query, int maxResults) {
        return request(query, maxResults);
    }

    private YoutubeSearchResponse request(String query, int maxResults) {
        try {
            YoutubeSearchResponse response = youtubeRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("regionCode", "KR")
                            .queryParam("videoEmbeddable", true)
                            .queryParam("q", query)
                            .queryParam("maxResults", maxResults)
                            .queryParam("key", properties.key())
                            .build())
                    .retrieve()
                    .body(YoutubeSearchResponse.class);

            if (response == null) {
                throw new YoutubeClientException("YouTube Search API returned an empty response");
            }
            return response;
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw new YoutubeClientException("YouTube Search API request failed", exception);
        }
    }
}
