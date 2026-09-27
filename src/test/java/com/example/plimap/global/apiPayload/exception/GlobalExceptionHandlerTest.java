package com.example.plimap.global.apiPayload.exception;

import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void BindException은_첫_번째_필드_오류_메시지로_400을_반환한다() throws Exception {
        mockMvc.perform(get("/exception-test/bind"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("첫 번째 필드 오류입니다."))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void BindException의_필드_오류_메시지가_없으면_기본_메시지를_반환한다() throws Exception {
        mockMvc.perform(get("/exception-test/bind-without-message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void BindException은_클래스_오류보다_필드_오류_메시지를_우선한다() throws Exception {
        mockMvc.perform(get("/exception-test/bind-with-global-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("필드 오류입니다."));
    }

    @Test
    void 필수_요청_헤더가_누락되면_헤더_이름을_포함한_400을_반환한다() throws Exception {
        mockMvc.perform(get("/exception-test/required-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_MISSING_HEADER"))
                .andExpect(jsonPath("$.message")
                        .value("필수 요청 헤더 'X-Device-Id'가 누락되었습니다."))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void 그_외_ServletRequestBindingException은_기존_400을_반환한다() throws Exception {
        mockMvc.perform(get("/exception-test/servlet-request-binding"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_BAD_REQUEST"));
    }

    @Test
    void 업로드_용량_초과는_413을_반환한다() throws Exception {
        mockMvc.perform(get("/exception-test/max-upload-size"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_413_CONTENT_TOO_LARGE"));
    }

    @Test
    void Validation_오류는_공통_필드와_함께_INFO로_기록하고_민감정보는_제외한다(
            CapturedOutput output
    ) throws Exception {
        mockMvc.perform(get("/exception-test/bind")
                        .queryParam("access_token", "query-secret")
                        .header("Authorization", "Bearer authorization-secret")
                        .header("Cookie", "refreshToken=cookie-secret"))
                .andExpect(status().isBadRequest());

        assertThat(output.getAll())
                .contains(
                        "INFO",
                        "status=400 code=COMMON_400_VALIDATION_FAILED "
                                + "method=GET uri=/exception-test/bind exception=BindException"
                )
                .doesNotContain(
                        "query-secret",
                        "authorization-secret",
                        "cookie-secret",
                        "BindException:"
                );
    }

    @Test
    void ConstraintViolationException은_공통_필드와_함께_INFO로_기록한다(
            CapturedOutput output
    ) {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/members/me");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of());

        int status = exceptionHandler
                .handleConstraintViolationException(exception, request)
                .getStatusCode()
                .value();

        assertThat(status).isEqualTo(400);
        assertThat(output.getAll()).contains(
                "INFO",
                "status=400 code=COMMON_400_VALIDATION_FAILED "
                        + "method=PATCH uri=/api/v1/members/me "
                        + "exception=ConstraintViolationException"
        );
    }

    @Test
    void BusinessException은_공통_필드와_함께_INFO로_기록한다(CapturedOutput output)
            throws Exception {
        mockMvc.perform(get("/exception-test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        assertThat(output.getAll()).contains(
                "INFO",
                "status=404 code=MEMBER_NOT_FOUND "
                        + "method=GET uri=/exception-test/business exception=MemberException"
        );
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_INFO로_기록한다(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/exception-test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_405_METHOD_NOT_ALLOWED"));

        assertThat(output.getAll()).contains(
                "INFO",
                "status=405 code=COMMON_405_METHOD_NOT_ALLOWED "
                        + "method=POST uri=/exception-test/business "
                        + "exception=HttpRequestMethodNotSupportedException"
        );
    }

    @Test
    void 존재하지_않는_리소스는_INFO로_기록한다(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing-resource");
        NoHandlerFoundException exception = new NoHandlerFoundException(
                "GET",
                "/missing-resource",
                HttpHeaders.EMPTY
        );

        int status = exceptionHandler
                .handleNotFoundException(exception, request)
                .getStatusCode()
                .value();

        assertThat(status)
                .isEqualTo(404);

        assertThat(output.getAll()).contains(
                "INFO",
                "status=404 code=COMMON_404_NOT_FOUND "
                        + "method=GET uri=/missing-resource exception=NoHandlerFoundException"
        );
    }

    @Test
    void 처리되지_않은_500_오류는_ERROR와_스택_트레이스로_기록한다(CapturedOutput output)
            throws Exception {
        mockMvc.perform(get("/exception-test/internal-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_500_INTERNAL_SERVER_ERROR"));

        assertThat(output.getAll())
                .contains(
                        "ERROR",
                        "status=500 code=COMMON_500_INTERNAL_SERVER_ERROR "
                                + "method=GET routeTemplate=/exception-test/internal-error "
                                + "exception=IllegalStateException",
                        "java.lang.IllegalStateException: unexpected error",
                        "GlobalExceptionHandlerTest$TestController.internalError"
                );
    }

    @RestController
    static class TestController {

        @GetMapping("/exception-test/bind")
        void bind() throws BindException {
            BindException exception = new BindException(new Object(), "request");
            exception.addError(new FieldError(
                    "request",
                    "firstField",
                    "첫 번째 필드 오류입니다."
            ));
            exception.addError(new FieldError(
                    "request",
                    "secondField",
                    "두 번째 필드 오류입니다."
            ));
            throw exception;
        }

        @GetMapping("/exception-test/bind-without-message")
        void bindWithoutMessage() throws BindException {
            BindException exception = new BindException(new Object(), "request");
            exception.addError(new FieldError(
                    "request",
                    "field",
                    null,
                    false,
                    null,
                    null,
                    null
            ));
            throw exception;
        }

        @GetMapping("/exception-test/bind-with-global-error")
        void bindWithGlobalError() throws BindException {
            BindException exception = new BindException(new Object(), "request");
            exception.addError(new ObjectError("request", "클래스 오류입니다."));
            exception.addError(new FieldError("request", "field", "필드 오류입니다."));
            throw exception;
        }

        @GetMapping("/exception-test/required-header")
        void requiredHeader(@RequestHeader("X-Device-Id") String deviceId) {
        }

        @GetMapping("/exception-test/servlet-request-binding")
        void servletRequestBinding() throws ServletRequestBindingException {
            throw new ServletRequestBindingException("요청 바인딩 오류");
        }

        @GetMapping("/exception-test/max-upload-size")
        void maxUploadSize() {
            throw new MaxUploadSizeExceededException(5 * 1024 * 1024);
        }

        @GetMapping("/exception-test/business")
        void business() {
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        @GetMapping("/exception-test/internal-error")
        void internalError() {
            throw new IllegalStateException("unexpected error");
        }
    }
}
