package com.example.plimap.global.external.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.plimap.global.external.youtube.dto.YoutubeSearchResponse;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

class YoutubeSearchClientImplTest {

    private static final String TEST_API_KEY = "test-youtube-api-key";

    private MockRestServiceServer server;
    private YoutubeSearchClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://youtube.test");
        server = MockRestServiceServer.bindTo(builder).build();
        YoutubeProperties properties = new YoutubeProperties(
                URI.create("https://youtube.test"),
                TEST_API_KEY,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );
        client = new YoutubeSearchClientImpl(builder.build(), properties);
    }

    @Test
    void 접미사_없이_한국_지역과_임베드_가능_조건으로_한번_검색한다() {
        server.expect(once(), request -> assertSearchRequest(
                        request.getURI(),
                        "아이유 밤편지",
                        5
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(multipleResults(), MediaType.APPLICATION_JSON));

        YoutubeSearchResponse response = client.search("아이유 밤편지", 5);

        assertThat(response.items())
                .extracting(item -> item.id() == null ? null : item.id().videoId())
                .containsExactly("first-video", "second-video");
        assertThat(response.items().getFirst().snippet())
                .isEqualTo(new YoutubeSearchResponse.Snippet("Song &amp; Song", "Artist"));
        server.verify();
    }

    @Test
    void 빈_검색_결과에도_클라이언트가_추가_호출하지_않는다() {
        // given
        server.expect(once(), request -> { })
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        // when, then
        assertThat(client.search("query", 5).items()).isEmpty();
        server.verify();
    }

    @Test
    void videoId가_없는_item을_그대로_역직렬화한다() {
        server.expect(once(), request -> { })
                .andRespond(withSuccess(
                        "{\"items\":[{\"id\":{}}]}",
                        MediaType.APPLICATION_JSON
                ));

        YoutubeSearchResponse response = client.search("query", 5);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id().videoId()).isNull();
        server.verify();
    }

    @Test
    void HTTP_오류를_YoutubeClientException으로_변환한다() {
        server.expect(once(), request -> { }).andRespond(withServerError());

        assertExternalApiError(() -> client.search("query", 5));
        server.verify();
    }

    @Test
    void 잘못된_JSON을_YoutubeClientException으로_변환한다() {
        server.expect(once(), request -> { })
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("query", 5))
                .isInstanceOfSatisfying(YoutubeClientException.class, exception ->
                        assertThat(exception.getCause()).isInstanceOf(RestClientException.class));
        server.verify();
    }

    @Test
    void timeout을_YoutubeClientException으로_변환한다() {
        server.expect(once(), request -> { })
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertExternalApiError(() -> client.search("query", 5));
        server.verify();
    }

    private void assertSearchRequest(URI uri, String expectedQuery, int expectedMaxResults) {
        var parameters = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        assertThat(uri.getPath()).isEqualTo("/search");
        assertThat(parameters.getFirst("part")).isEqualTo("snippet");
        assertThat(parameters.getFirst("type")).isEqualTo("video");
        assertThat(parameters.getFirst("regionCode")).isEqualTo("KR");
        assertThat(parameters.getFirst("videoEmbeddable")).isEqualTo("true");
        assertThat(parameters.getFirst("maxResults"))
                .isEqualTo(String.valueOf(expectedMaxResults));
        assertThat(parameters.getFirst("key")).isEqualTo(TEST_API_KEY);
        String query = parameters.getFirst("q");
        assertThat(UriUtils.decode(query, StandardCharsets.UTF_8)).isEqualTo(expectedQuery);
    }

    private void assertExternalApiError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(YoutubeClientException.class);
    }

    private String multipleResults() {
        return """
                {
                  "items": [
                    {"id": {"videoId": "first-video"}, "snippet": {"title": "Song &amp; Song", "channelTitle": "Artist", "description": "ignored"}},
                    {"id": {"videoId": "second-video"}}
                  ]
                }
                """;
    }
}
