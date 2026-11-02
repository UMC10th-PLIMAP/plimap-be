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
    ),
    // 탈퇴 시 저장된 토큰이 없거나 provider가 거부해 연결을 끊지 못한 경우. 앱은 해당 provider로
    // 다시 로그인해 받은 code를 탈퇴 요청에 담아 재시도한다. 401은 앱의 토큰 재발급 로직과 겹치므로 409를 쓴다.
    APPLE_REAUTH_REQUIRED(
            HttpStatus.CONFLICT,
            "AUTH_APPLE_REAUTH_REQUIRED",
            "탈퇴하려면 Apple 로그인을 한 번 더 진행해 주세요."
    ),
    GOOGLE_REAUTH_REQUIRED(
            HttpStatus.CONFLICT,
            "AUTH_GOOGLE_REAUTH_REQUIRED",
            "탈퇴하려면 Google 로그인을 한 번 더 진행해 주세요."
    ),
    SOCIAL_UNLINK_FAILED(
            HttpStatus.BAD_GATEWAY,
            "AUTH_SOCIAL_UNLINK_FAILED",
            "소셜 로그인 연결 해제 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
