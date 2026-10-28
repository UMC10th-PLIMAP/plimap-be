package com.example.plimap.global.logging;

import com.example.plimap.global.apiPayload.code.BaseErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class HttpErrorLogger {

    private static final String LOG_FORMAT =
            "status={} code={} method={} uri={} exception={}";
    private static final String ERROR_LOG_FORMAT =
            "status={} code={} method={} routeTemplate={} exception={}";
    private static final String HTTP_5XX_EVENT = "HTTP_5XX";
    private static final String UNRESOLVED_ROUTE = "<unresolved-route>";
    private static final String CLOUD_TRACE_HEADER = "X-Cloud-Trace-Context";
    private static final String DEFAULT_GCP_PROJECT_ID = "plimap";
    private static final Pattern CLOUD_TRACE_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{32})(?:/[0-9]+)?(?:;o=[01])?$"
    );

    private HttpErrorLogger() {
    }

    public static void info(HttpServletRequest request,
                            BaseErrorCode errorCode,
                            Throwable exception) {
        log.info(
                LOG_FORMAT,
                errorCode.getStatus().value(),
                errorCode.getCode(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );
    }

    public static void warn(HttpServletRequest request,
                            BaseErrorCode errorCode,
                            Throwable exception) {
        log.warn(
                LOG_FORMAT,
                errorCode.getStatus().value(),
                errorCode.getCode(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );
    }

    public static void error(HttpServletRequest request,
                             BaseErrorCode errorCode,
                             String responseMessage,
                             Throwable exception) {
        String routeTemplate = resolveRouteTemplate(request);
        String traceId = resolveTraceId(request);

        var logEvent = log.atError()
                .setCause(exception)
                .addKeyValue("event", HTTP_5XX_EVENT)
                .addKeyValue("status", errorCode.getStatus().value())
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("routeTemplate", routeTemplate)
                .addKeyValue("responseMessage", responseMessage)
                .addKeyValue("exceptionType", exception.getClass().getName());

        if (traceId != null) {
            logEvent
                    .addKeyValue("traceId", traceId)
                    .addKeyValue(
                            "logging.googleapis.com/trace",
                            "projects/%s/traces/%s".formatted(resolveProjectId(), traceId)
                    );
        }

        logEvent.log(
                ERROR_LOG_FORMAT,
                errorCode.getStatus().value(),
                errorCode.getCode(),
                request.getMethod(),
                routeTemplate,
                exception.getClass().getSimpleName()
        );
    }

    private static String resolveRouteTemplate(HttpServletRequest request) {
        Object routePattern = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        );
        if (routePattern == null || routePattern.toString().isBlank()) {
            return UNRESOLVED_ROUTE;
        }
        return routePattern.toString();
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceHeader = request.getHeader(CLOUD_TRACE_HEADER);
        if (traceHeader == null) {
            return null;
        }

        Matcher matcher = CLOUD_TRACE_PATTERN.matcher(traceHeader.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    private static String resolveProjectId() {
        String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (projectId == null || projectId.isBlank()) {
            return DEFAULT_GCP_PROJECT_ID;
        }
        return projectId;
    }
}
