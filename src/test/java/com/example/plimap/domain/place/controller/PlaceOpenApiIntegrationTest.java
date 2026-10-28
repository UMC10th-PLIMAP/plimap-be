package com.example.plimap.domain.place.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.plimap.support.PostgisContainerConfiguration;
import com.example.plimap.support.RedisContainerConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgisContainerConfiguration.class, RedisContainerConfiguration.class})
class PlaceOpenApiIntegrationTest {

    private static final String PLACE_SEARCH_PATH = "/api/v1/places/search";
    private static final String PLACE_SELECTION_PATH = "/api/v1/places/selections";
    private static final String PLACE_MAP_SELECTION_PATH = "/api/v1/places/map-selections";
    private static final String PLACE_DETAIL_PATH = "/api/v1/places/{placeId}";
    private static final String PLACE_BOOKMARK_PATH = "/api/v1/places/{placeId}/bookmarks";
    private static final String PLACE_BOOKMARK_LIST_PATH = "/api/v1/places/bookmarks";
    private static final String PLACE_POPULAR_LIST_PATH = "/api/v1/places/popular";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 장소_상세_OpenAPI는_최신_요청과_응답_계약을_노출한다() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(responseBody);

        JsonNode operation = openApi
                .path("paths")
                .path(PLACE_DETAIL_PATH)
                .path("get");
        assertThat(operation.path("description").asText())
                .contains("isTrackDetailAccessible");
        assertThat(operation.path("responses").has("200")).isTrue();
        assertThat(operation.path("responses").has("400")).isTrue();
        assertThat(operation.path("responses").has("401")).isTrue();
        assertThat(operation.path("responses").has("404")).isTrue();

        JsonNode latitude = findParameter(operation, "latitude");
        JsonNode longitude = findParameter(operation, "longitude");
        assertThat(latitude.path("required").asBoolean()).isTrue();
        assertThat(longitude.path("required").asBoolean()).isTrue();
        assertThat(latitude.path("schema").path("minimum").asDouble()).isEqualTo(-90.0);
        assertThat(latitude.path("schema").path("maximum").asDouble()).isEqualTo(90.0);
        assertThat(longitude.path("schema").path("minimum").asDouble()).isEqualTo(-180.0);
        assertThat(longitude.path("schema").path("maximum").asDouble()).isEqualTo(180.0);

        JsonNode properties = openApi
                .path("components")
                .path("schemas")
                .path("PlaceDetailResponse")
                .path("properties");
        assertThat(properties.has("placeId")).isTrue();
        assertThat(properties.has("placeName")).isTrue();
        assertThat(properties.has("category")).isTrue();
        assertThat(properties.has("address")).isTrue();
        assertThat(properties.has("roadAddress")).isTrue();
        assertThat(properties.has("latitude")).isTrue();
        assertThat(properties.has("longitude")).isTrue();
        assertThat(properties.has("distanceMeters")).isTrue();
        assertThat(properties.has("withinAccessRange")).isTrue();
        assertThat(properties.has("hasPin")).isTrue();
        assertThat(properties.has("firstPinCreatorNickname")).isTrue();
        assertThat(isNullableSchema(properties.path("firstPinCreatorNickname"))).isTrue();
        assertThat(properties.has("pinCount")).isTrue();
        assertThat(properties.has("bookmarkedByMe")).isTrue();
        assertThat(properties.has("pinnedByMe")).isTrue();
        assertThat(properties.has("detailAccessible")).isFalse();
        assertThat(properties.has("likedTrackAtPlaceByMe")).isFalse();
        assertThat(properties.has("followedMemberPinnedAtPlace")).isFalse();
    }

    @Test
    void 장소_북마크_OpenAPI는_PUT_DELETE와_본문_없는_계약을_노출한다() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(responseBody);

        JsonNode path = openApi.path("paths").path(PLACE_BOOKMARK_PATH);
        JsonNode put = path.path("put");
        JsonNode delete = path.path("delete");
        assertThat(put.isMissingNode()).isFalse();
        assertThat(delete.isMissingNode()).isFalse();
        assertThat(put.has("requestBody")).isFalse();
        assertThat(delete.has("requestBody")).isFalse();
        assertThat(put.path("responses").has("200")).isTrue();
        assertThat(put.path("responses").has("401")).isTrue();
        assertThat(put.path("responses").has("404")).isTrue();
        assertThat(delete.path("responses").has("200")).isTrue();
        assertThat(delete.path("responses").has("401")).isTrue();
        assertThat(delete.path("responses").has("404")).isTrue();

        JsonNode properties = openApi
                .path("components")
                .path("schemas")
                .path("PlaceBookmarkResult")
                .path("properties");
        assertThat(properties.has("placeId")).isTrue();
        assertThat(properties.has("bookmarkedByMe")).isTrue();
    }

    @Test
    void 저장한_장소_목록_OpenAPI는_좌표와_응답_계약을_노출한다() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(responseBody);

        JsonNode operation = openApi.path("paths").path(PLACE_BOOKMARK_LIST_PATH).path("get");
        assertThat(operation.path("description").asText())
                .contains("정확히 500m")
                .contains("최대 9개")
                .contains("HM-01");
        assertThat(operation.path("responses").has("200")).isTrue();
        assertThat(operation.path("responses").has("400")).isTrue();
        assertThat(operation.path("responses").has("401")).isTrue();

        JsonNode latitude = findParameter(operation, "latitude");
        JsonNode longitude = findParameter(operation, "longitude");
        assertThat(latitude.path("required").asBoolean()).isTrue();
        assertThat(longitude.path("required").asBoolean()).isTrue();
        assertThat(latitude.path("schema").path("minimum").asDouble()).isEqualTo(-90.0);
        assertThat(latitude.path("schema").path("maximum").asDouble()).isEqualTo(90.0);
        assertThat(longitude.path("schema").path("minimum").asDouble()).isEqualTo(-180.0);
        assertThat(longitude.path("schema").path("maximum").asDouble()).isEqualTo(180.0);

        JsonNode resultProperties = openApi.path("components").path("schemas")
                .path("PlaceBookmarkListResponse").path("properties");
        assertThat(resultProperties.has("items")).isTrue();

        JsonNode itemProperties = openApi.path("components").path("schemas")
                .path("PlaceBookmarkListItem").path("properties");
        assertThat(itemProperties.has("placeId")).isTrue();
        assertThat(itemProperties.has("placeName")).isTrue();
        assertThat(itemProperties.has("firstPinCreatorNickname")).isTrue();
        assertThat(itemProperties.has("distanceMeters")).isTrue();
        assertThat(isNullableSchema(itemProperties.path("firstPinCreatorNickname"))).isTrue();
    }

    @Test
    void 인기_장소_목록_OpenAPI는_scope_좌표와_응답_계약을_노출한다() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(responseBody);

        JsonNode operation = openApi.path("paths").path(PLACE_POPULAR_LIST_PATH).path("get");
        assertThat(operation.has("requestBody")).isFalse();
        assertThat(operation.path("tags").get(0).asText()).isEqualTo("Place");
        assertThat(openApi.path("security").get(0).has("JWT TOKEN")).isTrue();
        assertThat(operation.has("security")).isFalse();
        assertThat(operation.path("description").asText())
                .contains("최대 6개")
                .contains("반경 제한 없이")
                .contains("실제 거리 ASC, 활성 PIN 수 DESC, placeId ASC")
                .contains("REGION3, REGION2, REGION1, GLOBAL")
                .contains("활성 PIN 수 DESC, 실제 거리 ASC, placeId ASC")
                .contains("이전 결과를 버리고")
                .contains("H 결과가 없으면 전국")
                .contains("전국 결과는 6개 미만이어도 반환")
                .contains("HM-01-01");
        assertThat(operation.path("responses").has("200")).isTrue();
        assertThat(operation.path("responses").has("400")).isTrue();
        assertThat(operation.path("responses").has("401")).isTrue();
        assertThat(operation.path("responses").has("502")).isTrue();
        assertThat(operation.path("responses").has("504")).isTrue();

        JsonNode scope = findParameter(operation, "scope");
        JsonNode latitude = findParameter(operation, "latitude");
        JsonNode longitude = findParameter(operation, "longitude");
        assertThat(scope.path("required").asBoolean()).isTrue();
        assertThat(enumValues(scope.path("schema")))
                .containsExactlyInAnyOrder("NEARBY", "GLOBAL");
        assertThat(latitude.path("required").asBoolean()).isTrue();
        assertThat(longitude.path("required").asBoolean()).isTrue();
        assertThat(latitude.path("schema").path("minimum").asDouble()).isEqualTo(-90.0);
        assertThat(latitude.path("schema").path("maximum").asDouble()).isEqualTo(90.0);
        assertThat(longitude.path("schema").path("minimum").asDouble()).isEqualTo(-180.0);
        assertThat(longitude.path("schema").path("maximum").asDouble()).isEqualTo(180.0);

        JsonNode resultProperties = openApi.path("components").path("schemas")
                .path("PlacePopularListResponse").path("properties");
        assertThat(resultProperties.has("scopeLevel")).isTrue();
        assertThat(enumValues(resultProperties.path("scopeLevel")))
                .containsExactlyInAnyOrder("REGION3", "REGION2", "REGION1", "GLOBAL");
        assertThat(isNullableSchema(resultProperties.path("scopeLevel"))).isTrue();
        assertThat(resultProperties.has("scopeName")).isTrue();
        assertThat(isNullableSchema(resultProperties.path("scopeName"))).isTrue();
        assertThat(resultProperties.has("items")).isTrue();

        JsonNode itemProperties = openApi.path("components").path("schemas")
                .path("PlacePopularListItem").path("properties");
        assertThat(itemProperties.has("placeId")).isTrue();
        assertThat(itemProperties.has("placeName")).isTrue();
        assertThat(itemProperties.has("distanceMeters")).isTrue();
        assertThat(itemProperties.has("pinCount")).isTrue();
        assertThat(itemProperties.has("representativeImageUrl")).isTrue();
        assertThat(isNullableSchema(itemProperties.path("representativeImageUrl"))).isTrue();
    }

    @Test
    void 장소_검색_OpenAPI는_PLACE와_ADDRESS_응답_계약을_노출한다() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(responseBody);

        String description = openApi
                .path("paths")
                .path(PLACE_SEARCH_PATH)
                .path("get")
                .path("description")
                .asText();
        assertThat(description)
                .contains("주소를 먼저 검색")
                .contains("정확도순")
                .contains("거리 계산");

        JsonNode searchItemSchema = findSearchItemSchema(openApi);
        JsonNode properties = searchItemSchema.path("properties");
        assertThat(enumValues(properties.path("resultType")))
                .containsExactlyInAnyOrder("PLACE", "ADDRESS");
        assertThat(properties.path("providerPlaceId").path("description").asText())
                .contains("ADDRESS 결과는 null");
        assertThat(properties.path("category").path("description").asText())
                .contains("ADDRESS 결과는 null");
        assertThat(properties.path("placeName").path("description").asText())
                .contains("roadAddress, address 순");
        assertThat(properties.path("roadAddress").path("description").asText())
                .contains("없는 경우 null");
    }

    @Test
    void 장소_선택_OpenAPI는_ADDRESS_요청과_응답_계약을_노출한다() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(responseBody);

        JsonNode operation = openApi
                .path("paths")
                .path(PLACE_SELECTION_PATH)
                .path("post");
        assertThat(operation.path("description").asText())
                .contains("ADDRESS_SEARCH")
                .contains("전체 지번 주소 기준");

        JsonNode selectionSchema = findSelectionRequestSchema(openApi);
        JsonNode properties = selectionSchema.path("properties");
        assertThat(enumValues(properties.path("resultType")))
                .containsExactlyInAnyOrder("PLACE", "ADDRESS");
        assertThat(properties.path("providerPlaceId").path("description").asText())
                .contains("ADDRESS이면 null");
        assertThat(properties.path("category").path("description").asText())
                .contains("ADDRESS이면 null");

        JsonNode responseSchema = findSelectionResponseSchema(openApi);
        assertThat(enumValues(responseSchema.path("properties").path("source")))
                .containsExactlyInAnyOrder(
                        "PLACE_SEARCH",
                        "ADDRESS_SEARCH",
                        "MAP_SELECTION"
                );
    }

    @Test
    void 지도_선택_장소_OpenAPI는_세_판정_상태와_응답_계약을_노출한다() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(responseBody);

        JsonNode operation = openApi
                .path("paths")
                .path(PLACE_MAP_SELECTION_PATH)
                .path("post");
        assertThat(operation.path("description").asText())
                .contains("PLACE_SEARCH")
                .contains("MAP_SELECTION")
                .contains("PN-02-03-b")
                .contains("국가명과 시/도명");
        assertThat(operation.path("responses").has("200")).isTrue();
        assertThat(operation.path("responses").has("400")).isTrue();
        assertThat(operation.path("responses").has("401")).isTrue();
        assertThat(operation.path("responses").has("502")).isTrue();
        assertThat(operation.path("responses").has("504")).isTrue();

        JsonNode requestSchema = resolveReferencedSchema(
                openApi,
                operation.path("requestBody")
                        .path("content")
                        .path("application/json")
                        .path("schema")
        );
        JsonNode requestProperties = requestSchema.path("properties");
        assertThat(requestProperties.has("latitude")).isTrue();
        assertThat(requestProperties.has("longitude")).isTrue();
        assertThat(requestProperties.has("address")).isTrue();
        assertThat(requestProperties.has("roadAddress")).isTrue();
        assertThat(requestProperties.has("placeName")).isFalse();
        assertThat(textValues(requestSchema.path("required")))
                .containsExactlyInAnyOrder("latitude", "longitude", "address");
        assertThat(requestProperties.path("address").path("description").asText())
                .contains("전체 지번 주소");
        assertThat(requestProperties.path("roadAddress").path("description").asText())
                .contains("전체 도로명 주소");

        JsonNode decisionProperties = openApi
                .path("components")
                .path("schemas")
                .path("PlaceMapSelectionDecisionResponse")
                .path("properties");
        assertThat(enumValues(decisionProperties.path("status")))
                .containsExactlyInAnyOrder(
                        "MAP_SELECTION_CONFIRMED",
                        "PLACE_SEARCH_RECOMMENDED",
                        "PLACE_SEARCH_REQUIRED"
                );
        assertThat(isNullableSchema(decisionProperties.path("mapSelection"))).isTrue();
        assertThat(isNullableSchema(decisionProperties.path("recommendedPlace"))).isTrue();
        assertThat(isNullableSchema(decisionProperties.path("buildingName"))).isTrue();
        assertThat(decisionProperties.path("mapSelection").path("description").asText())
                .contains("MAP_SELECTION_CONFIRMED");
        assertThat(decisionProperties.path("recommendedPlace").path("description").asText())
                .contains("PLACE_SEARCH_RECOMMENDED");
        assertThat(decisionProperties.path("buildingName").path("description").asText())
                .contains("PLACE_SEARCH_REQUIRED");

        JsonNode mapSelectionProperties = resolveReferencedSchema(
                openApi,
                decisionProperties.path("mapSelection")
        ).path("properties");
        assertThat(enumValues(mapSelectionProperties.path("source")))
                .containsExactly("MAP_SELECTION");
        assertThat(mapSelectionProperties.path("placeName").path("description").asText())
                .contains("국가명과 시/도명")
                .contains("축약 주소");

        JsonNode recommendedProperties = resolveReferencedSchema(
                openApi,
                decisionProperties.path("recommendedPlace")
        )
                .path("properties");
        assertThat(enumValues(recommendedProperties.path("source")))
                .containsExactly("PLACE_SEARCH");
        assertThat(isNullableSchema(recommendedProperties.path("category"))).isTrue();
        assertThat(isNullableSchema(recommendedProperties.path("roadAddress"))).isTrue();
        assertThat(isNullableSchema(recommendedProperties.path("placeId"))).isFalse();
        assertThat(isNullableSchema(recommendedProperties.path("placeName"))).isFalse();
        assertThat(isNullableSchema(recommendedProperties.path("address"))).isFalse();
        assertThat(isNullableSchema(recommendedProperties.path("source"))).isFalse();
        assertThat(isNullableSchema(recommendedProperties.path("latitude"))).isFalse();
        assertThat(isNullableSchema(recommendedProperties.path("longitude"))).isFalse();
        assertThat(isNullableSchema(recommendedProperties.path("distanceMeters"))).isFalse();
    }

    private JsonNode findSearchItemSchema(JsonNode openApi) {
        for (Map.Entry<String, JsonNode> entry :
                openApi.path("components").path("schemas").properties()) {
            JsonNode properties = entry.getValue().path("properties");
            if (properties.has("resultType")
                    && properties.has("providerPlaceId")
                    && properties.has("distanceMeters")
                    && properties.has("firstPinCreatorNickname")) {
                return entry.getValue();
            }
        }
        throw new AssertionError("Place search item schema not found");
    }

    private JsonNode findParameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError("OpenAPI parameter not found: " + name);
    }

    private JsonNode findSelectionRequestSchema(JsonNode openApi) {
        for (Map.Entry<String, JsonNode> entry :
                openApi.path("components").path("schemas").properties()) {
            JsonNode properties = entry.getValue().path("properties");
            if (properties.has("resultType")
                    && properties.has("providerPlaceId")
                    && properties.has("userLatitude")
                    && properties.has("userLongitude")) {
                return entry.getValue();
            }
        }
        throw new AssertionError("Place selection request schema not found");
    }

    private JsonNode findSelectionResponseSchema(JsonNode openApi) {
        for (Map.Entry<String, JsonNode> entry :
                openApi.path("components").path("schemas").properties()) {
            JsonNode properties = entry.getValue().path("properties");
            if (properties.has("source")
                    && properties.has("withinAccessRange")
                    && properties.has("bookmarkedByMe")) {
                return entry.getValue();
            }
        }
        throw new AssertionError("Place selection response schema not found");
    }

    private JsonNode resolveReferencedSchema(JsonNode openApi, JsonNode propertySchema) {
        String reference = propertySchema.path("$ref").asText();
        int separatorIndex = reference.lastIndexOf('/');
        if (separatorIndex < 0 || separatorIndex == reference.length() - 1) {
            throw new AssertionError("Referenced schema not found: " + propertySchema);
        }
        return openApi
                .path("components")
                .path("schemas")
                .path(reference.substring(separatorIndex + 1));
    }

    private List<String> enumValues(JsonNode schema) {
        List<String> values = new ArrayList<>();
        schema.path("enum").forEach(value -> values.add(value.asText()));
        return values;
    }

    private List<String> textValues(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(value -> values.add(value.asText()));
        return values;
    }

    private boolean isNullableSchema(JsonNode schema) {
        if (schema.path("nullable").asBoolean()
                || "null".equals(schema.path("type").asText())) {
            return true;
        }
        for (JsonNode type : schema.path("type")) {
            if ("null".equals(type.asText())) {
                return true;
            }
        }
        return false;
    }
}
