package com.example.plimap.global.swagger;

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
import java.util.Set;
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
class SwaggerErrorResponsesOpenApiIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get",
            "post",
            "put",
            "patch",
            "delete"
    );
    private static final Set<String> OPERATIONS_WITHOUT_KNOWN_FAILURE_RESPONSE = Set.of(
            "GET /api/v1/auth/csrf"
    );
    private static final String ERROR_SCHEMA_REFERENCE =
            "#/components/schemas/ErrorApiResponse";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 모든_API의_실패_응답은_공통_스키마와_named_example을_노출한다() throws Exception {
        JsonNode openApi = fetchOpenApi();

        JsonNode errorSchema = openApi
                .path("components")
                .path("schemas")
                .path("ErrorApiResponse");
        assertThat(errorSchema.isMissingNode()).isFalse();
        assertThat(errorSchema.path("properties").has("isSuccess")).isTrue();
        assertThat(errorSchema.path("properties").has("code")).isTrue();
        assertThat(errorSchema.path("properties").has("message")).isTrue();
        assertThat(errorSchema.path("properties").has("result")).isTrue();

        for (Map.Entry<String, JsonNode> pathEntry : openApi.path("paths").properties()) {
            if (!pathEntry.getKey().startsWith("/api/v1/")) {
                continue;
            }
            for (Map.Entry<String, JsonNode> operationEntry : pathEntry.getValue().properties()) {
                if (!HTTP_METHODS.contains(operationEntry.getKey())) {
                    continue;
                }
                String operationName = operationEntry.getKey().toUpperCase()
                        + " " + pathEntry.getKey();
                assertThat(operationEntry.getValue().path("responses").properties())
                        .as("%s 성공 응답", operationName)
                        .anySatisfy(response -> assertThat(response.getKey())
                                .startsWith("2"));
                List<Map.Entry<String, JsonNode>> failureResponses =
                        failureResponses(operationEntry.getValue());

                if (!OPERATIONS_WITHOUT_KNOWN_FAILURE_RESPONSE.contains(operationName)) {
                    assertThat(failureResponses)
                            .as("%s 실패 응답", operationName)
                            .isNotEmpty();
                }

                for (Map.Entry<String, JsonNode> responseEntry : failureResponses) {
                    assertFailureResponse(
                            operationName,
                            responseEntry.getKey(),
                            responseEntry.getValue()
                    );
                }
            }
        }
    }

    @Test
    void 데모_로그인은_회원_ID나_Bearer_인증_입력_없이_문서화한다() throws Exception {
        // given, when
        JsonNode operation = fetchOpenApi().path("paths").path("/api/v1/auth/demo").path("post");

        // then
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("requestBody").isMissingNode()).isTrue();
        assertThat(operation.path("parameters").isMissingNode()).isTrue();
        assertThat(operation.path("security").isArray()).isTrue();
        assertThat(operation.path("security").isEmpty()).isTrue();
    }

    private JsonNode fetchOpenApi() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody);
    }

    private List<Map.Entry<String, JsonNode>> failureResponses(JsonNode operation) {
        List<Map.Entry<String, JsonNode>> failures = new ArrayList<>();
        for (Map.Entry<String, JsonNode> response : operation.path("responses").properties()) {
            if (isFailureStatus(response.getKey())) {
                failures.add(response);
            }
        }
        return failures;
    }

    private boolean isFailureStatus(String statusCode) {
        try {
            return Integer.parseInt(statusCode) >= 400;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void assertFailureResponse(
            String operationName,
            String statusCode,
            JsonNode response
    ) {
        String context = operationName + " " + statusCode;
        JsonNode content = response.path("content").path("application/json");
        assertThat(content.isMissingNode())
                .as("%s application/json content", context)
                .isFalse();
        assertThat(content.path("schema").path("$ref").asText())
                .as("%s error schema", context)
                .isEqualTo(ERROR_SCHEMA_REFERENCE);

        JsonNode examples = content.path("examples");
        assertThat(examples.isObject())
                .as("%s named examples", context)
                .isTrue();
        assertThat(examples.isEmpty())
                .as("%s named examples", context)
                .isFalse();

        for (Map.Entry<String, JsonNode> exampleEntry : examples.properties()) {
            JsonNode value = exampleEntry.getValue().path("value");
            assertThat(value.path("isSuccess").asBoolean())
                    .as("%s %s isSuccess", context, exampleEntry.getKey())
                    .isFalse();
            assertThat(value.path("code").asText())
                    .as("%s %s code", context, exampleEntry.getKey())
                    .isNotBlank();
            assertThat(exampleEntry.getKey())
                    .as("%s example name", context)
                    .startsWith(value.path("code").asText());
            assertThat(value.path("message").asText())
                    .as("%s %s message", context, exampleEntry.getKey())
                    .isNotBlank();
            assertThat(value.path("result").isNull())
                    .as("%s %s result", context, exampleEntry.getKey())
                    .isTrue();
        }
    }
}
