package com.example.plimap.global.external.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 앱의 serverAuthCode는 웹 OAuth 클라이언트(webClientId)로 발급되므로 웹 로그인과 같은 자격 증명으로 교환한다.
@ConfigurationProperties(prefix = "google.auth")
public record GoogleAuthProperties(
        String clientId,
        String clientSecret
) {
}
