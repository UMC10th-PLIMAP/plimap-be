package com.example.plimap.domain.auth.controller.docs;

import static com.example.plimap.global.swagger.CommonSwaggerErrorExamples.JSON_MESSAGE_SEPARATOR;
import static com.example.plimap.global.swagger.CommonSwaggerErrorExamples.JSON_PREFIX;
import static com.example.plimap.global.swagger.CommonSwaggerErrorExamples.JSON_SUFFIX;

final class AuthSwaggerErrorExamples {

    static final String MEMBER_NOT_FOUND =
            JSON_PREFIX + "MEMBER_NOT_FOUND"
                    + JSON_MESSAGE_SEPARATOR + "존재하지 않는 사용자입니다." + JSON_SUFFIX;
    static final String NICKNAME_FORBIDDEN_WORD =
            JSON_PREFIX + "MEMBER_NICKNAME_FORBIDDEN_WORD"
                    + JSON_MESSAGE_SEPARATOR + "사용할 수 없는 닉네임입니다." + JSON_SUFFIX;
    static final String NICKNAME_DUPLICATE =
            JSON_PREFIX + "MEMBER_NICKNAME_DUPLICATE"
                    + JSON_MESSAGE_SEPARATOR + "이미 사용 중인 닉네임입니다." + JSON_SUFFIX;
    static final String ALREADY_ONBOARDED =
            JSON_PREFIX + "MEMBER_ALREADY_ONBOARDED"
                    + JSON_MESSAGE_SEPARATOR + "이미 온보딩을 완료한 사용자입니다." + JSON_SUFFIX;
    static final String TERMS_NOT_FOUND =
            JSON_PREFIX + "TERMS_NOT_FOUND"
                    + JSON_MESSAGE_SEPARATOR + "해당 유형의 활성 약관을 찾을 수 없습니다." + JSON_SUFFIX;
    static final String AGREEMENT_REQUIRED =
            JSON_PREFIX + "TERMS_AGREEMENT_REQUIRED"
                    + JSON_MESSAGE_SEPARATOR + "필수 약관에 모두 동의해야 합니다." + JSON_SUFFIX;
    static final String INVALID_REFRESH_TOKEN =
            JSON_PREFIX + "AUTH_INVALID_REFRESH_TOKEN"
                    + JSON_MESSAGE_SEPARATOR
                    + "유효하지 않거나 만료된 리프레시 토큰입니다."
                    + JSON_SUFFIX;
    static final String REFRESH_TOKEN_MISMATCH =
            JSON_PREFIX + "AUTH_REFRESH_TOKEN_MISMATCH"
                    + JSON_MESSAGE_SEPARATOR
                    + "저장된 리프레시 토큰과 일치하지 않습니다."
                    + JSON_SUFFIX;
    static final String TEST_TOKEN_ISSUE_UNAUTHORIZED =
            JSON_PREFIX + "AUTH_TEST_TOKEN_ISSUE_UNAUTHORIZED"
                    + JSON_MESSAGE_SEPARATOR
                    + "테스트 토큰 발급 인증에 실패했습니다."
                    + JSON_SUFFIX;

    static final String DEMO_LOGIN_UNAVAILABLE =
            JSON_PREFIX + "AUTH_DEMO_LOGIN_UNAVAILABLE"
                    + JSON_MESSAGE_SEPARATOR + "지금은 로그인 없이 사용해보기를 이용할 수 없습니다." + JSON_SUFFIX;

    private AuthSwaggerErrorExamples() {
    }
}
