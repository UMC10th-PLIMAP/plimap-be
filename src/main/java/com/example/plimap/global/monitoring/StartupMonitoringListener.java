package com.example.plimap.global.monitoring;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline.TimelineEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;

/** Opt-in startup diagnostics registered before the application context is created. */
@Slf4j
public final class StartupMonitoringListener
        implements ApplicationListener<SpringApplicationEvent>, Ordered {

    private static final String PREFIX = "plimap.monitoring.startup.";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_$#.-]{1,160}");
    private static final Set<String> STEP_NAMES = Set.of(
            "spring.beans.instantiate", "spring.beans.smart-initialize",
            "spring.context.refresh", "spring.context.beans.post-process",
            "spring.context.bean-factory.post-process",
            "spring.context.beandef-registry.post-process",
            "spring.context.config-classes.parse", "spring.context.config-classes.enhance",
            "spring.boot.application.context-prepared", "spring.boot.application.context-loaded",
            "spring.boot.application.started", "spring.boot.application.ready"
    );

    private final long startedAtNanos;
    private BufferingApplicationStartup recorder;
    private int capacity;
    private int maxSteps;
    private String service;
    private String revision;
    private SpringApplication application;
    private boolean readyLogged;

    public StartupMonitoringListener(long startedAtNanos) {
        this.startedAtNanos = startedAtNanos;
    }

    @Override
    public int getOrder() {
        // ConfigData must already have populated application.yml and profile properties.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(SpringApplicationEvent event) {
        if (event instanceof ApplicationEnvironmentPreparedEvent prepared) {
            configure(prepared);
            return;
        }
        if (recorder == null || event.getSpringApplication() != application) {
            return;
        }
        if (event instanceof ApplicationStartedEvent started
                && started.getApplicationContext().getParent() == null) {
            logLifecycle("APPLICATION_STARTED", started.getTimeTaken());
        } else if (event instanceof ApplicationReadyEvent ready
                && ready.getApplicationContext().getParent() == null && !readyLogged) {
            readyLogged = true;
            logLifecycle("APPLICATION_READY", ready.getTimeTaken());
            logSteps();
            ready.getApplicationContext().setApplicationStartup(ApplicationStartup.DEFAULT);
            ready.getApplicationContext().getBeanFactory()
                    .setApplicationStartup(ApplicationStartup.DEFAULT);
            application.setApplicationStartup(ApplicationStartup.DEFAULT);
            recorder = null;
        }
    }

    private void configure(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (!environment.getProperty(PREFIX + "enabled", Boolean.class, false)) {
            return;
        }
        capacity = environment.getProperty(PREFIX + "buffer-capacity", Integer.class, 2048);
        maxSteps = environment.getProperty(PREFIX + "max-steps", Integer.class, 10);
        if (capacity < 64 || capacity > 8192 || maxSteps < 1 || maxSteps > 50) {
            throw new IllegalArgumentException(
                    "시작 계측 buffer-capacity는 64~8192, max-steps는 1~50이어야 합니다."
            );
        }
        service = safeIdentifier(environment.getProperty("K_SERVICE"));
        revision = safeIdentifier(environment.getProperty("K_REVISION"));
        application = event.getSpringApplication();
        recorder = new BufferingApplicationStartup(capacity);
        recorder.addFilter(step -> STEP_NAMES.contains(step.getName()));
        event.getSpringApplication().setApplicationStartup(recorder);
    }

    private void logLifecycle(String event, Duration timeTaken) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        log.atInfo()
                .addKeyValue("event", event)
                .addKeyValue("elapsed_ms", elapsedMs)
                .addKeyValue("spring_elapsed_ms", timeTaken.toMillis())
                .addKeyValue("service", service)
                .addKeyValue("revision", revision)
                .log("event={} elapsed_ms={} spring_elapsed_ms={} service={} revision={}",
                        event, elapsedMs, timeTaken.toMillis(), service, revision);
    }

    private void logSteps() {
        List<TimelineEvent> events = recorder.drainBufferedTimeline().getEvents();
        List<TimelineEvent> slowest = events.stream()
                .sorted(Comparator.comparing(TimelineEvent::getDuration).reversed())
                .limit(maxSteps)
                .toList();
        log.atInfo()
                .addKeyValue("event", "STARTUP_SUMMARY")
                .addKeyValue("recorded_steps", events.size())
                .addKeyValue("reported_steps", slowest.size())
                .addKeyValue("buffer_capacity", capacity)
                .addKeyValue("summary_truncated", events.size() > maxSteps)
                .addKeyValue("capacity_reached", events.size() >= capacity)
                .addKeyValue("service", service)
                .addKeyValue("revision", revision)
                .log("event=STARTUP_SUMMARY recorded_steps={} reported_steps={} buffer_capacity={} "
                                + "summary_truncated={} capacity_reached={} service={} revision={}",
                        events.size(), slowest.size(), capacity, events.size() > maxSteps,
                        events.size() >= capacity, service, revision);
        for (TimelineEvent event : slowest) {
            StartupStep step = event.getStartupStep();
            String beanName = "unknown";
            for (StartupStep.Tag tag : step.getTags()) {
                if ("beanName".equals(tag.getKey())) {
                    beanName = safeIdentifier(tag.getValue());
                }
            }
            log.atInfo()
                    .addKeyValue("event", "STARTUP_STEP")
                    .addKeyValue("step_id", step.getId())
                    .addKeyValue("parent_id", step.getParentId())
                    .addKeyValue("step", step.getName())
                    .addKeyValue("duration_ms", event.getDuration().toMillis())
                    .addKeyValue("bean", beanName)
                    .addKeyValue("service", service)
                    .addKeyValue("revision", revision)
                    .log("event=STARTUP_STEP step_id={} parent_id={} step={} duration_ms={} "
                                    + "bean={} service={} revision={}",
                            step.getId(), step.getParentId(), step.getName(),
                            event.getDuration().toMillis(), beanName, service, revision);
        }
    }

    private static String safeIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches() ? value : "unknown";
    }
}
