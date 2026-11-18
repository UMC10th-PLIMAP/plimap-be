package com.example.plimap.global.external.apple;

import org.springframework.boot.context.properties.ConfigurationProperties;

// privateKey는 App Store Connect에서 받은 .p8 키(PEM) 내용이다. 비밀값이라 환경변수로만 주입한다.
@ConfigurationProperties(prefix = "apple.auth")
public record AppleAuthProperties(
        String clientId,
        String teamId,
        String keyId,
        String privateKey
) {
}
