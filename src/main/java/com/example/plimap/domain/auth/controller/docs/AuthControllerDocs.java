package com.example.plimap.domain.auth.controller.docs;

import com.example.plimap.domain.auth.dto.request.AuthReqDTO;
import com.example.plimap.domain.auth.dto.response.AuthResponse;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.member.dto.request.MemberReqDTO;
import com.example.plimap.domain.member.dto.request.TermsReqDTO;
import com.example.plimap.domain.member.dto.response.MemberResponse;
import com.example.plimap.domain.member.dto.response.TermsResponse;
import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.swagger.CommonSwaggerErrorExamples;
import com.example.plimap.global.swagger.ErrorApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.web.csrf.CsrfToken;

@Tag(name = "Auth", description = "인증 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "앱(네이티브) 로그인",
            description = """
                    앱이 카카오/구글/애플 SDK로 발급받은 토큰(카카오는 액세스 토큰, 구글/애플은 ID 토큰)을 전달하면
                    서버가 provider에 직접 토큰을 검증하고 accessToken/refreshToken을 JSON 응답으로 반환합니다.
                    기존 웹 로그인(OAuth2Login 리다이렉트 + 쿠키)과는 별개의 흐름입니다.

                    정지/자동탈퇴 회원은 200 응답에 accessToken 없이 status/reasonCategory/reasonDetail/suspendedUntil 등
                    제재 정보만 채워서 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 검증 실패 또는 지원하지 않는 provider인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED",
                                    summary = "요청 값 검증 실패",
                                    value = CommonSwaggerErrorExamples.VALIDATION_FAILED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "provider 토큰 검증에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "AUTH_APP_TOKEN_VERIFICATION_FAILED",
                                    summary = "앱 토큰 검증 실패",
                                    value = AuthSwaggerErrorExamples.APP_TOKEN_VERIFICATION_FAILED
                            )
                    ))
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공(제재 회원은 토큰 없이 제재 정보만 포함)")
    ApiResponse<AuthResponse.AppLogin> appLogin(AuthReqDTO.AppLogin request);

    @Operation(
            summary = "CSRF 토큰 발급",
            description = "쿠키 인증 상태 변경 요청의 X-XSRF-TOKEN 헤더에 사용할 CSRF 토큰을 반환합니다."
    )
    ApiResponse<AuthResponse.CsrfToken> getCsrfToken(
            @Parameter(hidden = true) CsrfToken csrfToken
    );

    @Operation(
            summary = "온보딩 (닉네임/프로필 설정)",
            description = "최초 가입 후 닉네임과 프로필 정보를 등록하여 온보딩을 완료합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 본문이 올바르지 않거나 사용할 수 없는 닉네임인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "요청 값 검증 실패",
                                            value = CommonSwaggerErrorExamples.VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_MALFORMED_JSON",
                                            summary = "잘못된 JSON 본문",
                                            value = CommonSwaggerErrorExamples.MALFORMED_JSON
                                    ),
                                    @ExampleObject(
                                            name = "MEMBER_NICKNAME_FORBIDDEN_WORD",
                                            summary = "사용 불가 닉네임",
                                            value = AuthSwaggerErrorExamples.NICKNAME_FORBIDDEN_WORD
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "인증 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = AuthSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 온보딩했거나 닉네임이 중복된 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "MEMBER_ALREADY_ONBOARDED",
                                            summary = "이미 온보딩 완료",
                                            value = AuthSwaggerErrorExamples.ALREADY_ONBOARDED
                                    ),
                                    @ExampleObject(
                                            name = "MEMBER_NICKNAME_DUPLICATE",
                                            summary = "닉네임 중복",
                                            value = AuthSwaggerErrorExamples.NICKNAME_DUPLICATE
                                    )
                            }
                    ))
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    ApiResponse<MemberResponse.Onboarding> onboarding(AuthMember authMember, MemberReqDTO.Onboarding request);

    @Operation(
            summary = "약관 동의 여부 조회",
            description = """
                    현재 활성화된 약관 유형별로, 로그인한 회원이 동의했는지 여부와 동의 일시를 조회합니다.

                    **약관 유형(type)**
                    - SERVICE (필수): 플리맵 이용약관
                    - PRIVACY (필수): 개인정보 수집 및 이용 동의
                    - LOCATION (필수): 위치기반서비스 이용약관
                    - MARKETING (선택): 마케팅 정보 수신 동의
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "TERMS_AGREEMENT_STATUS_RETRIEVED_SUCCESS",
                      "message": "약관 동의 여부를 조회했습니다.",
                      "result": [
                        { "type": "SERVICE", "agreed": true, "agreedAt": "2026-07-13T07:19:16.301Z" },
                        { "type": "PRIVACY", "agreed": true, "agreedAt": "2026-07-13T07:19:16.301Z" },
                        { "type": "LOCATION", "agreed": false, "agreedAt": null },
                        { "type": "MARKETING", "agreed": false, "agreedAt": null }
                      ]
                    }
                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    ))
    })
    ApiResponse<List<TermsResponse.Result>> getTermsAgreementStatus(AuthMember authMember);

    @Operation(
            summary = "약관 동의",
            description = """
                    약관 유형별 동의 여부를 등록합니다. 활성 필수 약관에 모두 동의하지 않으면 실패합니다.

                    **약관 유형(type)**
                    - SERVICE (필수): 플리맵 이용약관
                    - PRIVACY (필수): 개인정보 수집 및 이용 동의
                    - LOCATION (필수): 위치기반서비스 이용약관
                    - MARKETING (선택): 마케팅 정보 수신 동의
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "TERMS_AGREED_SUCCESS",
                      "message": "약관 동의가 완료되었습니다.",
                      "result": [
                        {
                          "type": "SERVICE",
                          "agreed": true,
                          "agreedAt": "2026-07-13T07:19:16.301Z"
                        }
                      ]
                    }
                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 검증에 실패했거나 필수 약관에 동의하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "요청 값 검증 실패",
                                            value = CommonSwaggerErrorExamples.VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_MALFORMED_JSON",
                                            summary = "잘못된 JSON 본문",
                                            value = CommonSwaggerErrorExamples.MALFORMED_JSON
                                    ),
                                    @ExampleObject(
                                            name = "TERMS_AGREEMENT_REQUIRED",
                                            summary = "필수 약관 미동의",
                                            value = AuthSwaggerErrorExamples.AGREEMENT_REQUIRED
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 또는 활성 약관을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "MEMBER_NOT_FOUND",
                                            summary = "회원 없음",
                                            value = AuthSwaggerErrorExamples.MEMBER_NOT_FOUND
                                    ),
                                    @ExampleObject(
                                            name = "TERMS_NOT_FOUND",
                                            summary = "활성 약관 없음",
                                            value = AuthSwaggerErrorExamples.TERMS_NOT_FOUND
                                    )
                            }
                    ))
    })
    ApiResponse<List<TermsResponse.Result>> agreeToTerms(AuthMember authMember, TermsReqDTO.Agree request);

    @Operation(
            summary = "로그아웃",
            description = "현재 액세스 토큰을 서버 측에서 무효화(블랙리스트 등록)하고, 저장된 리프레시 토큰과 accessToken/refreshToken 쿠키를 삭제합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 정보가 없거나 유효하지 않은 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "COMMON_401_UNAUTHORIZED",
                            summary = "인증 필요",
                            value = CommonSwaggerErrorExamples.UNAUTHORIZED
                    )
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response);

    @Operation(
            summary = "토큰 재발급",
            description = "refreshToken 쿠키를 검증하여 새로운 Access/Refresh Token을 발급하고 쿠키를 갱신합니다(Refresh Token Rotation)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "리프레시 토큰이 유효하지 않거나 저장된 토큰과 일치하지 않는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "AUTH_INVALID_REFRESH_TOKEN",
                                            summary = "유효하지 않은 리프레시 토큰",
                                            value = AuthSwaggerErrorExamples.INVALID_REFRESH_TOKEN
                                    ),
                                    @ExampleObject(
                                            name = "AUTH_REFRESH_TOKEN_MISMATCH",
                                            summary = "저장된 리프레시 토큰과 불일치",
                                            value = AuthSwaggerErrorExamples.REFRESH_TOKEN_MISMATCH
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "활성 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = AuthSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    ))
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    ApiResponse<Void> reissue(HttpServletRequest request, HttpServletResponse response);
}
