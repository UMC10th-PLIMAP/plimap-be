package com.example.plimap.global.config;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.AotDetector;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/** Keeps the generated prod Bean graph and the effective runtime configuration consistent. */
@Slf4j
public final class SpringAotRuntimeValidator
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        boolean enabled = AotDetector.useGeneratedArtifacts();
        boolean configured = event.getEnvironment()
                .getProperty("spring.aot.enabled", Boolean.class, enabled);
        if (enabled != configured) {
            throw new IllegalStateException("Spring AOT system property and runtime configuration must agree.");
        }
        if (enabled && !Arrays.equals(event.getEnvironment().getActiveProfiles(), new String[]{"prod"})) {
            throw new IllegalStateException("Generated Spring AOT artifacts require only the prod profile.");
        }
        log.atInfo()
                .addKeyValue("event", "SPRING_AOT_RUNTIME")
                .addKeyValue("enabled", enabled)
                .log("event=SPRING_AOT_RUNTIME enabled={}", enabled);
    }
}
