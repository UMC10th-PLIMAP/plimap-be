package com.example.plimap.global.external.apple;

import com.example.plimap.global.external.oidc.OidcTokenResponse;

public interface AppleAuthClient {

    // 애플이 code를 거부(invalid_grant)하면 SocialAuthRejectedException을 던진다.
    OidcTokenResponse exchangeCode(String authorizationCode);

    // 애플이 토큰을 거부(invalid_grant/invalid_token)하면 false,
    // 그 외 4xx(설정 오류)·네트워크·서버 오류면 SocialAuthClientException을 던진다.
    boolean revoke(String refreshToken);
}
