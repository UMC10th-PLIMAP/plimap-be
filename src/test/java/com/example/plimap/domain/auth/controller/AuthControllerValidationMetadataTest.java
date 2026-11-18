package com.example.plimap.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.plimap.domain.auth.dto.request.AuthReqDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class AuthControllerValidationMetadataTest {

    @Test
    void 인증_컨트롤러의_인터페이스와_요청_검증_선언이_일치한다() {
        // given
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            // when
            Throwable thrown = catchThrowable(() -> validator.getConstraintsForClass(AuthController.class));

            // then
            assertThat(thrown).isNull();
            var method = validator.getConstraintsForClass(AuthController.class)
                    .getConstraintsForMethod("appLogin", AuthReqDTO.AppLogin.class);
            assertThat(method.getParameterDescriptors().getFirst().isCascaded()).isTrue();
        }
    }
}
