package com.example.plimap.domain.auth.controller.docs;

import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.swagger.ErrorApiResponse;
import com.example.plimap.global.swagger.CommonSwaggerErrorExamples;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "Auth", description = "인증 API")
public interface DemoAuthControllerDocs {

    @SecurityRequirements
    @Operation(summary = "로그인 없이 사용해보기", description = """
            서버에 지정된 공용 테스트 계정으로 접속합니다. 요청 본문과 소셜 로그인은 필요하지 않습니다.
            먼저 GET /api/v1/auth/csrf를 호출하고, 같은 브라우저의 쿠키와 응답 result.token을
            X-XSRF-TOKEN 헤더로 전송하세요. Swagger UI는 CSRF 쿠키를 읽어 헤더를 자동으로 전달합니다.
            성공 시 24시간 accessToken HttpOnly 쿠키를 설정하며 기존 refreshToken 쿠키는 삭제합니다.
            리프레시 토큰은 발급하지 않습니다. 이후 GET /api/v1/members/me로 로그인 상태를 확인하세요.
            만료되면 이 API를 다시 호출합니다. 계정의 데이터와 일반 회원 기능을 함께 사용합니다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "공용 테스트 계정 접속 성공 (AUTH_DEMO_LOGIN_SUCCESS)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "CSRF 토큰 누락 또는 불일치",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(name = "COMMON_403_FORBIDDEN", value = CommonSwaggerErrorExamples.FORBIDDEN))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "테스트 계정이 없거나 정지·탈퇴·삭제된 경우 (MEMBER_NOT_FOUND)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(name = "MEMBER_NOT_FOUND", value = AuthSwaggerErrorExamples.MEMBER_NOT_FOUND))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "기능 비활성화, 계정 ID 미설정 또는 일반 회원이 아닌 경우 (AUTH_DEMO_LOGIN_UNAVAILABLE)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(name = "AUTH_DEMO_LOGIN_UNAVAILABLE", value = AuthSwaggerErrorExamples.DEMO_LOGIN_UNAVAILABLE)))
    })
    ApiResponse<Void> loginDemo(@Parameter(hidden = true) HttpServletResponse response);
}
