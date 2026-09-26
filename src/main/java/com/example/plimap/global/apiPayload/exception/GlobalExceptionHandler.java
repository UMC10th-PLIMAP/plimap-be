package com.example.plimap.global.apiPayload.exception;

import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.apiPayload.code.BaseErrorCode;
import com.example.plimap.global.apiPayload.code.GeneralErrorCode;
import com.example.plimap.global.logging.HttpErrorLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        // 대부분의 BusinessException은 exception.getMessage() == errorCode.getMessage()라
        // 동작 변화가 없다. 일부(예: 정지 만료일을 담은 MemberException)는 생성 시점에
        // 동적 메시지를 넘겨 고정 메시지 대신 그 메시지를 응답에 노출한다.
        return failure(exception.getErrorCode(), exception.getMessage(), request, exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return failure(
                GeneralErrorCode.VALIDATION_FAILED,
                getFirstErrorMessage(exception.getBindingResult().getFieldErrors()),
                request,
                exception
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException exception,
            HttpServletRequest request
    ) {
        return failure(
                GeneralErrorCode.VALIDATION_FAILED,
                getFirstErrorMessage(exception.getBindingResult().getFieldErrors()),
                request,
                exception
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        return failure(
                GeneralErrorCode.VALIDATION_FAILED,
                getFirstErrorMessage(exception.getAllErrors()),
                request,
                exception
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(GeneralErrorCode.VALIDATION_FAILED.getMessage());

        return failure(
                GeneralErrorCode.VALIDATION_FAILED,
                message,
                request,
                exception
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.MISSING_PARAMETER, request, exception);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPartException(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.MISSING_PARAMETER, request, exception);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.CONTENT_TOO_LARGE, request, exception);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeaderException(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        String message = "필수 요청 헤더 '%s'가 누락되었습니다.".formatted(exception.getHeaderName());
        return failure(GeneralErrorCode.MISSING_HEADER, message, request, exception);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleServletRequestBindingException(
            ServletRequestBindingException exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.BAD_REQUEST, request, exception);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.TYPE_MISMATCH, request, exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.MALFORMED_JSON, request, exception);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(
            Exception exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.NOT_FOUND, request, exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.METHOD_NOT_ALLOWED, request, exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        return failure(GeneralErrorCode.INTERNAL_SERVER_ERROR, request, exception);
    }

    private ResponseEntity<ApiResponse<Void>> failure(
            BaseErrorCode errorCode,
            HttpServletRequest request,
            Exception exception
    ) {
        logException(errorCode, errorCode.getMessage(), request, exception);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode));
    }

    private ResponseEntity<ApiResponse<Void>> failure(
            BaseErrorCode errorCode,
            String message,
            HttpServletRequest request,
            Exception exception
    ) {
        logException(errorCode, message, request, exception);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode, message));
    }

    private void logException(BaseErrorCode errorCode,
                              String responseMessage,
                              HttpServletRequest request,
                              Exception exception) {
        if (errorCode.getStatus().is4xxClientError()) {
            HttpErrorLogger.info(request, errorCode, exception);
            return;
        }

        HttpErrorLogger.error(request, errorCode, responseMessage, exception);
    }

    private String getFirstErrorMessage(List<? extends MessageSourceResolvable> errors) {
        return errors.stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(GeneralErrorCode.VALIDATION_FAILED.getMessage());
    }
}
