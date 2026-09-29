package com.example.plimap.global.external.oidc;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "oidc.jwks")
@Validated
public record JwksRestClientProperties(
        @NotNull(message = "JWKS 조회 연결 제한 시간은 필수입니다.")
        Duration connectTimeout,
        @NotNull(message = "JWKS 조회 응답 제한 시간은 필수입니다.")
        Duration readTimeout
) {
}
