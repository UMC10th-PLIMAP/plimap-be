package com.example.plimap.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.mock.env.MockEnvironment;

class StartupMonitoringListenerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(StartupMonitoringListener.class);
    private ListAppender<ILoggingEvent> appender;
    private SpringApplication application;
    private StartupMonitoringListener listener;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        application = new SpringApplication(EmptyConfig.class);
        listener = new StartupMonitoringListener(System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void 기본_실행에서는_시작_버퍼와_진단_로그를_만들지_않는다() {
        // given
        MockEnvironment environment = new MockEnvironment();

        // when
        configure(environment);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            listener.onApplicationEvent(new ApplicationStartedEvent(
                    application, new String[0], context, Duration.ofSeconds(1)));
            listener.onApplicationEvent(new ApplicationReadyEvent(
                    application, new String[0], context, Duration.ofSeconds(2)));
        }

        // then
        assertThat(application.getApplicationStartup()).isSameAs(ApplicationStartup.DEFAULT);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 시작과_준비_시점을_구분하고_가장_느린_단계만_출력한다() {
        // given
        configure(enabled().withProperty("plimap.monitoring.startup.max-steps", "1"));
        var recorder = (BufferingApplicationStartup) application.getApplicationStartup();
        var parent = application.getApplicationStartup().start("spring.context.refresh");
        var child = application.getApplicationStartup().start("spring.beans.instantiate");
        child.tag("beanName", "profileImageStorage");
        child.end();
        parent.end();

        // when
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            listener.onApplicationEvent(new ApplicationStartedEvent(
                    application, new String[0], context, Duration.ofMillis(100)));
            listener.onApplicationEvent(new ApplicationReadyEvent(
                    application, new String[0], context, Duration.ofMillis(120)));
        }

        // then
        assertThat(events("APPLICATION_STARTED")).hasSize(1);
        assertThat(fields(events("APPLICATION_STARTED").getFirst()))
                .containsEntry("spring_elapsed_ms", 100L)
                .containsEntry("service", "plimap-api-prod")
                .containsEntry("revision", "revision-001");
        assertThat(fields(events("APPLICATION_READY").getFirst()))
                .containsEntry("spring_elapsed_ms", 120L);
        assertThat(fields(events("STARTUP_SUMMARY").getFirst()))
                .containsEntry("recorded_steps", 2)
                .containsEntry("reported_steps", 1)
                .containsEntry("summary_truncated", true);
        assertThat(fields(events("STARTUP_STEP").getFirst()))
                .containsEntry("step", "spring.context.refresh")
                .containsEntry("step_id", parent.getId());
        assertThat(recorder.getBufferedTimeline().getEvents()).isEmpty();
        assertThat(application.getApplicationStartup()).isSameAs(ApplicationStartup.DEFAULT);
    }

    @Test
    void 임의_태그와_단계명_및_줄바꿈이_있는_식별자는_출력하지_않는다() {
        // given
        configure(enabled().withProperty("K_REVISION", "revision\nforged=true"));
        var step = application.getApplicationStartup().start("spring.beans.instantiate");
        step.tag("beanName", "safeBean").tag("password", "test-secret-never-log");
        step.tag("beanType", "sensitive-type-never-log");
        step.end();
        application.getApplicationStartup().start("custom-secret-step").end();

        // when
        ready();

        // then
        assertThat(events("STARTUP_STEP")).hasSize(1);
        assertThat(fields(events("STARTUP_STEP").getFirst()))
                .containsEntry("bean", "safeBean")
                .containsEntry("revision", "unknown")
                .containsOnlyKeys("event", "step_id", "parent_id", "step", "duration_ms",
                        "bean", "service", "revision");
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(
                    "test-secret-never-log", "sensitive-type-never-log",
                    "custom-secret-step", "forged=true");
            assertThat(fields(event).toString()).doesNotContain(
                    "test-secret-never-log", "sensitive-type-never-log", "forged=true");
        });
    }

    @Test
    void 버퍼_상한을_넘는_단계를_보관하지_않고_포화_가능성을_표시한다() {
        // given
        configure(enabled().withProperty("plimap.monitoring.startup.buffer-capacity", "64"));
        for (int i = 0; i < 80; i++) {
            application.getApplicationStartup().start("spring.beans.instantiate").end();
        }

        // when
        ready();

        // then
        assertThat(fields(events("STARTUP_SUMMARY").getFirst()))
                .containsEntry("recorded_steps", 64)
                .containsEntry("capacity_reached", true)
                .containsEntry("reported_steps", 10);
        assertThat(appender.list).hasSize(12);
    }

    @Test
    void 잘못된_설정은_값을_노출하지_않고_거부한다() {
        // given
        MockEnvironment environment = enabled()
                .withProperty("plimap.monitoring.startup.max-steps", "51");

        // when / then
        assertThatThrownBy(() -> configure(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시작 계측 buffer-capacity는 64~8192, max-steps는 1~50이어야 합니다.");
    }

    @Test
    void 자식_컨텍스트와_다른_애플리케이션의_이벤트는_기록하지_않는다() {
        // given
        configure(enabled());

        // when
        try (GenericApplicationContext parent = new GenericApplicationContext();
             GenericApplicationContext child = new GenericApplicationContext(parent)) {
            listener.onApplicationEvent(new ApplicationReadyEvent(
                    application, new String[0], child, Duration.ofSeconds(1)));
            listener.onApplicationEvent(new ApplicationReadyEvent(
                    new SpringApplication(EmptyConfig.class), new String[0], parent,
                    Duration.ofSeconds(1)));
        }

        // then
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 실제_Spring_기동에서_준비_이벤트와_Bean_시작_단계를_기록한다() {
        // given
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        application.addListeners(listener);
        application.addListeners((ApplicationListener<ApplicationPreparedEvent>) event -> {
            // Boot initializes its logging system before the context is prepared.
            appender.start();
            logger.addAppender(appender);
        });

        // when
        try (var context = application.run("--plimap.monitoring.startup.enabled=true",
                "--spring.main.banner-mode=off")) {
            assertThat(context.getBean("sampleBean")).isNotNull();
            assertThat(context.getApplicationStartup()).isSameAs(ApplicationStartup.DEFAULT);
            assertThat(context.getBeanFactory().getApplicationStartup())
                    .isSameAs(ApplicationStartup.DEFAULT);
        }

        // then
        assertThat(events("APPLICATION_STARTED")).hasSize(1);
        assertThat(events("APPLICATION_READY")).hasSize(1);
        assertThat(events("STARTUP_SUMMARY")).hasSize(1);
        assertThat(events("STARTUP_STEP")).isNotEmpty();
    }

    private MockEnvironment enabled() {
        return new MockEnvironment()
                .withProperty("plimap.monitoring.startup.enabled", "true")
                .withProperty("K_SERVICE", "plimap-api-prod")
                .withProperty("K_REVISION", "revision-001");
    }

    private void configure(MockEnvironment environment) {
        listener.onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(), application, new String[0], environment));
    }

    private void ready() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            listener.onApplicationEvent(new ApplicationReadyEvent(
                    application, new String[0], context, Duration.ofSeconds(1)));
        }
    }

    private List<ILoggingEvent> events(String name) {
        return appender.list.stream()
                .filter(event -> name.equals(fields(event).get("event")))
                .toList();
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
        }
        return fields;
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
        @Bean
        Object sampleBean() {
            return new Object();
        }
    }
}
