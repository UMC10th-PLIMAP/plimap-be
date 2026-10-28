package com.example.plimap.global.config;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

public final class GoogleCloudSeverityJsonMembersCustomizer
        implements StructuredLoggingJsonMembersCustomizer<Object> {

    private static final String LOG_LEVEL_PATH = "level";

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(
                JsonWriter.ValueProcessor.of(String.class, this::toGoogleCloudSeverity)
                        .whenHasUnescapedPath(LOG_LEVEL_PATH)
        );
    }

    private String toGoogleCloudSeverity(String level) {
        return "WARN".equals(level) ? "WARNING" : level;
    }
}
