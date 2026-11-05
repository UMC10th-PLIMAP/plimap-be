package com.example.plimap.global.external.oidc;

public class SocialAuthClientException extends RuntimeException {

    public SocialAuthClientException(String message) {
        super(message);
    }

    public SocialAuthClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
