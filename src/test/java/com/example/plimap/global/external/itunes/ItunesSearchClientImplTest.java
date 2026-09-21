package com.example.plimap.global.external.itunes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.plimap.global.external.itunes.dto.ItunesSearchResponse;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UriComponentsBuilder;

class ItunesSearchClientImplTest {

    private MockRestServiceServer server;
    private ItunesSearchClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ItunesSearchClientImpl(builder.build());
    }

    @Test
    void iTunes_검색에_필요한_파라미터를_전달하고_응답을_변환한다() {
        server.expect(once(), request -> {
                    var parameters = UriComponentsBuilder.fromUri(request.getURI())
                            .build()
                            .getQueryParams();
                    assertThat(request.getURI().getPath()).isEqualTo("/search");
                    assertThat(parameters.getFirst("country")).isEqualTo("US");
                    assertThat(parameters.getFirst("media")).isEqualTo("music");
                    assertThat(parameters.getFirst("entity")).isEqualTo("song");
                    assertThat(parameters.getFirst("limit")).isEqualTo("20");
                    assertThat(UriUtils.decode(
                            parameters.getFirst("term"), StandardCharsets.UTF_8))
                            .isEqualTo("아이유 밤편지");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        ItunesSearchResponse response = client.search("아이유 밤편지", 20);

        assertThat(response.results()).containsExactly(new ItunesSearchResponse.Item(
                123L,
                "밤편지",
                "아이유",
                "Palette",
                "https://image.example/cover.jpg",
                "https://audio.example/preview.m4a",
                253000
        ));
        server.verify();
    }

    @Test
    void 외부_HTTP_오류를_Track_예외로_변환한다() {
        server.expect(once(), request -> { })
                .andRespond(withServerError());

        assertExternalApiError(() -> client.search("아이유", 20));
        server.verify();
    }

    @Test
    void timeout_오류를_Track_예외로_변환한다() {
        server.expect(once(), request -> { })
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertExternalApiError(() -> client.search("아이유", 20));
        server.verify();
    }

    @Test
    void 역직렬화_오류를_Track_예외로_변환한다() {
        server.expect(once(), request -> { })
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("아이유", 20))
                .isInstanceOfSatisfying(ItunesClientException.class, exception ->
                        assertThat(exception.getCause()).isInstanceOf(RestClientException.class));
        server.verify();
    }

    private void assertExternalApiError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ItunesClientException.class);
    }

    private String successResponse() {
        return """
                {
                  "resultCount": 1,
                  "results": [
                    {
                      "trackId": 123,
                      "trackName": "밤편지",
                      "artistName": "아이유",
                      "collectionName": "Palette",
                      "artworkUrl100": "https://image.example/cover.jpg",
                      "previewUrl": "https://audio.example/preview.m4a",
                      "trackTimeMillis": 253000
                    }
                  ]
                }
                """;
    }
}
