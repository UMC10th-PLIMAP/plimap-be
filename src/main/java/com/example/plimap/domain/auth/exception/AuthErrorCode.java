package com.example.plimap.domain.auth.exception;

import com.example.plimap.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "유효하지 않거나 만료된 리프레시 토큰입니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_MISMATCH", "저장된 리프레시 토큰과 일치하지 않습니다."),
    TEST_TOKEN_ISSUE_UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_TEST_TOKEN_ISSUE_UNAUTHORIZED",
            "테스트 토큰 발급 인증에 실패했습니다."
    ),
    APP_TOKEN_VERIFICATION_FAILED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_APP_TOKEN_VERIFICATION_FAILED",
            "앱에서 전달한 토큰을 검증하지 못했습니다."
    ),
    APP_LOGIN_PROVIDER_TIMEOUT(
            HttpStatus.GATEWAY_TIMEOUT,
            "AUTH_APP_LOGIN_PROVIDER_TIMEOUT",
            "카카오 서버 응답이 지연되어 로그인을 완료하지 못했습니다."
    ),
    APP_LOGIN_NONCE_INVALID(
            HttpStatus.UNAUTHORIZED,
            "AUTH_APP_LOGIN_NONCE_INVALID",
            "유효하지 않거나 이미 사용된 로그인 시도입니다. 처음부터 다시 로그인해 주세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
