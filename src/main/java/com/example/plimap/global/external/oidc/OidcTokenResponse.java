package com.example.plimap.global.external.oidc;

import com.fasterxml.jackson.annotation.JsonProperty;

// 애플/구글 토큰 엔드포인트(authorization code 교환) 응답 중 연결 끊기에 필요한 값만 받는다.
// 구글은 이미 동의한 계정이면 refresh token 없이 access token만 내려줄 수 있다.
public record OidcTokenResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("refresh_token")
        String refreshToken,
        @JsonProperty("id_token")
        String idToken
) {
}
