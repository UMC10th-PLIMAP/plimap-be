package com.example.plimap.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

class GoogleCloudSeverityJsonMembersCustomizerTest {

    private final GoogleCloudSeverityJsonMembersCustomizer customizer =
            new GoogleCloudSeverityJsonMembersCustomizer();

    @Test
    void WARN을_Google_Cloud_WARNING으로_변환한다() {
        String json = writeLevel("WARN");

        assertThat(json).isEqualTo("{\"severity\":\"WARNING\"}");
    }

    @Test
    void WARN이_아닌_로그_레벨은_변경하지_않는다() {
        String json = writeLevel("ERROR");

        assertThat(json).isEqualTo("{\"severity\":\"ERROR\"}");
    }

    private String writeLevel(String level) {
        JsonWriter<Object> writer = JsonWriter.of(members -> {
            members.add("level", value -> value);
            members.applyingNameProcessor((path, name) ->
                    "level".equals(path.toUnescapedString()) ? "severity" : name
            );
            customizer.customize(members);
        });
        return writer.writeToString(level);
    }
}
