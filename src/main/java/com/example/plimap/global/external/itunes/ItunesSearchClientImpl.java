package com.example.plimap.global.external.itunes;

import com.example.plimap.global.external.itunes.dto.ItunesSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class ItunesSearchClientImpl implements ItunesSearchClient {

    private final RestClient itunesRestClient;

    @Override
    public ItunesSearchResponse search(String keyword, int limit) {
        try {
            ItunesSearchResponse response = itunesRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("country", "US")
                            .queryParam("media", "music")
                            .queryParam("entity", "song")
                            .queryParam("limit", limit)
                            .queryParam("term", keyword)
                            .build())
                    .retrieve()
                    .body(ItunesSearchResponse.class);

            if (response == null) {
                throw new ItunesClientException("iTunes Search API returned an empty response");
            }
            return response;
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw new ItunesClientException("iTunes Search API request failed", exception);
        }
    }
}
