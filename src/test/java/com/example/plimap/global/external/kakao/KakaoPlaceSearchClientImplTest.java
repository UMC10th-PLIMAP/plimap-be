package com.example.plimap.global.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.plimap.global.external.kakao.dto.KakaoPlaceSearchResponse;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

class KakaoPlaceSearchClientImplTest {

    private MockRestServiceServer server;
    private KakaoPlaceSearchClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalProperties properties = new KakaoLocalProperties(
                URI.create("https://kakao.test"),
                "test-rest-api-key",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );
        client = new KakaoPlaceSearchClientImpl(builder.build(), properties);
    }

    @Test
    void 카카오_장소_검색에_인증과_위치_정확도순_파라미터를_전달한다() {
        server.expect(once(), request -> {
                    var parameters = UriComponentsBuilder.fromUri(request.getURI())
                            .build()
                            .getQueryParams();
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/v2/local/search/keyword.json");
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                            .isEqualTo("KakaoAK test-rest-api-key");
                    assertThat(UriUtils.decode(
                            parameters.getFirst("query"),
                            StandardCharsets.UTF_8
                    )).isEqualTo("여의도 한강공원");
                    assertThat(parameters.getFirst("x")).isEqualTo("126.9326");
                    assertThat(parameters.getFirst("y")).isEqualTo("37.5283");
                    assertThat(parameters.getFirst("sort")).isEqualTo("accuracy");
                    assertThat(parameters).doesNotContainKeys("radius", "rect");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        KakaoPlaceSearchResponse response = client.search(
                "여의도 한강공원",
                37.5283,
                126.9326
        );

        assertThat(response.documents()).containsExactly(new KakaoPlaceSearchResponse.Document(
                "26338954",
                "한강",
                "여행 > 관광,명소 > 공원",
                "서울특별시 영등포구 여의도동",
                "서울특별시 영등포구 여의동로",
                "126.9326",
                "37.5283",
                "470"
        ));
        server.verify();
    }

    @Test
    void 카카오_HTTP_오류를_외부_API_예외로_변환한다() {
        server.expect(once(), request -> { })
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search("한강", 37.5283, 126.9326))
                .isInstanceOf(KakaoClientException.class)
                .isNotInstanceOf(KakaoClientTimeoutException.class);
        server.verify();
    }

    @Test
    void 카카오_timeout을_timeout_예외로_변환한다() {
        server.expect(once(), request -> { })
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.search("한강", 37.5283, 126.9326))
                .isInstanceOf(KakaoClientTimeoutException.class);
        server.verify();
    }

    @Test
    void 카카오_응답을_역직렬화할_수_없으면_외부_API_예외를_던진다() {
        server.expect(once(), request -> { })
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("한강", 37.5283, 126.9326))
                .isInstanceOf(KakaoClientException.class);
        server.verify();
    }

    private String successResponse() {
        return """
                {
                  "documents": [
                    {
                      "id": "26338954",
                      "place_name": "한강",
                      "category_name": "여행 > 관광,명소 > 공원",
                      "address_name": "서울특별시 영등포구 여의도동",
                      "road_address_name": "서울특별시 영등포구 여의동로",
                      "x": "126.9326",
                      "y": "37.5283",
                      "distance": "470"
                    }
                  ]
                }
                """;
    }
}
