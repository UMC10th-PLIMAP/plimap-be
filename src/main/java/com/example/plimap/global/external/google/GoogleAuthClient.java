package com.example.plimap.global.external.google;

import com.example.plimap.global.external.oidc.OidcTokenResponse;

public interface GoogleAuthClient {

    OidcTokenResponse exchangeCode(String serverAuthCode);

    // 구글이 토큰을 거부(4xx)하면 false, 네트워크/서버 오류면 SocialAuthClientException을 던진다.
    boolean revoke(String refreshToken);
}
