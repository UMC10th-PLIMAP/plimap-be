package com.example.plimap.domain.auth.exception;

import com.example.plimap.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    DEMO_LOGIN(HttpStatus.OK, "AUTH_DEMO_LOGIN_SUCCESS", "테스트 계정으로 접속했습니다."),
    TOKEN_REISSUED(HttpStatus.OK, "AUTH_TOKEN_REISSUED_SUCCESS", "토큰이 재발급되었습니다."),
    CSRF_TOKEN_ISSUED(HttpStatus.OK, "AUTH_CSRF_TOKEN_ISSUED_SUCCESS", "CSRF 토큰이 발급되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
