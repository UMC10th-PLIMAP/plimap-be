package com.example.plimap.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plimap.auth.demo")
public record DemoAuthProperties(boolean enabled, Long memberId) {
}
