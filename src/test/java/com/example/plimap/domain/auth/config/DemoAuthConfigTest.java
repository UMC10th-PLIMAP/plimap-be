package com.example.plimap.domain.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DemoAuthConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DemoAuthConfig.class);

    @Test
    void 기본값은_비활성화이며_회원_ID가_없다() {
        // given, when, then
        runner.run(context -> {
            DemoAuthProperties properties = context.getBean(DemoAuthProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.memberId()).isNull();
        });
    }

    @Test
    void 환경별_활성화와_회원_ID를_바인딩한다() {
        // given, when, then
        runner.withPropertyValues("plimap.auth.demo.enabled=true", "plimap.auth.demo.member-id=42")
                .run(context -> {
                    DemoAuthProperties properties = context.getBean(DemoAuthProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.memberId()).isEqualTo(42L);
                });
    }
}
