package com.example.plimap.domain.auth.exception;

import com.example.plimap.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    DEMO_LOGIN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_DEMO_LOGIN_UNAVAILABLE", "지금은 로그인 없이 사용해보기를 이용할 수 없습니다."),
    DEMO_ACCOUNT_WITHDRAWAL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "AUTH_DEMO_ACCOUNT_WITHDRAWAL_NOT_ALLOWED", "테스트 계정은 회원 탈퇴를 할 수 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "유효하지 않거나 만료된 리프레시 토큰입니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_MISMATCH", "저장된 리프레시 토큰과 일치하지 않습니다."),
    TEST_TOKEN_ISSUE_UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_TEST_TOKEN_ISSUE_UNAUTHORIZED",
            "테스트 토큰 발급 인증에 실패했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
