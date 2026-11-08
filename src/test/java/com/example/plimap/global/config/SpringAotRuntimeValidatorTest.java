package com.example.plimap.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.springframework.aot.AotDetector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

class SpringAotRuntimeValidatorTest {

    @Test
    void 일반_JVM은_다른_프로필도_허용한다() {
        // given
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        // when
        Throwable thrown = validate(false, environment);

        // then
        assertThat(thrown).isNull();
    }

    @Test
    void AOT는_prod_단독_프로필을_허용한다() {
        // given
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        // when
        Throwable thrown = validate(true, environment);

        // then
        assertThat(thrown).isNull();
    }

    @Test
    void AOT는_prod와_다른_프로필의_혼용을_거부한다() {
        // given
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "dev");

        // when
        Throwable thrown = validate(true, environment);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only the prod profile");
    }

    @Test
    void AOT는_런타임_프로퍼티_불일치를_거부한다() {
        // given
        MockEnvironment environment = new MockEnvironment().withProperty("spring.aot.enabled", "false");
        environment.setActiveProfiles("prod");

        // when
        Throwable thrown = validate(true, environment);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must agree");
    }

    private Throwable validate(boolean enabled, MockEnvironment environment) {
        try (var detector = mockStatic(AotDetector.class)) {
            detector.when(AotDetector::useGeneratedArtifacts).thenReturn(enabled);
            var event = new ApplicationEnvironmentPreparedEvent(
                    new DefaultBootstrapContext(), new SpringApplication(Object.class), new String[0], environment
            );
            return catchThrowable(() -> new SpringAotRuntimeValidator().onApplicationEvent(event));
        }
    }
}
