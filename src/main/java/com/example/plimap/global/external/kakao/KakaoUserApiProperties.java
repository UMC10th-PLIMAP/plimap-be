package com.example.plimap.global.external.kakao;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "kakao.user-api")
@Validated
public record KakaoUserApiProperties(
        @NotNull(message = "Kakao User API 기본 URL은 필수입니다.")
        URI baseUrl,
        @NotNull(message = "Kakao User API 연결 제한 시간은 필수입니다.")
        Duration connectTimeout,
        @NotNull(message = "Kakao User API 응답 제한 시간은 필수입니다.")
        Duration readTimeout
) {
}
