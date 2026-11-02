package com.example.plimap.global.external.apple;

import com.example.plimap.global.external.oidc.OidcTokenResponse;

public interface AppleAuthClient {

    OidcTokenResponse exchangeCode(String authorizationCode);

    // 애플이 토큰을 거부(4xx)하면 false, 네트워크/서버 오류면 SocialAuthClientException을 던진다.
    boolean revoke(String refreshToken);
}
