package com.example.plimap.global.external.oidc;

import org.springframework.web.client.HttpClientErrorException;

// provider가 code/토큰 자체를 거부한 경우(invalid_grant, invalid_token). 사용자가 다시 로그인하면 해결된다.
// invalid_client 같은 서버 설정 오류나 5xx·타임아웃은 재로그인으로 해결되지 않으므로 상위 예외로 구분한다.
public class SocialAuthRejectedException extends SocialAuthClientException {

    public SocialAuthRejectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public static boolean isRejection(HttpClientErrorException exception) {
        String body = exception.getResponseBodyAsString();
        return body.contains("invalid_grant") || body.contains("invalid_token");
    }
}
