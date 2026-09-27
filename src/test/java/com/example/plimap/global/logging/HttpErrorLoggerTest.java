package com.example.plimap.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.plimap.global.apiPayload.code.GeneralErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HttpErrorLoggerTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(HttpErrorLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void 서버_오류는_응답_메시지와_라우트_템플릿을_구조화_필드로_기록한다() {
        MockHttpServletRequest request = request("/api/v1/pins/42");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/pins/{pinId}"
        );
        IllegalStateException exception = new IllegalStateException("unexpected error");

        HttpErrorLogger.error(
                request,
                GeneralErrorCode.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다.",
                exception
        );

        ILoggingEvent event = appender.list.getFirst();
        Map<String, Object> fields = keyValues(event);

        assertThat(fields)
                .containsEntry("event", "HTTP_5XX")
                .containsEntry("status", 500)
                .containsEntry("errorCode", "COMMON_500_INTERNAL_SERVER_ERROR")
                .containsEntry("method", "GET")
                .containsEntry("routeTemplate", "/api/v1/pins/{pinId}")
                .containsEntry("responseMessage", "서버 내부 오류가 발생했습니다.")
                .containsEntry("exceptionType", IllegalStateException.class.getName())
                .doesNotContainKey("uri");
        assertThat(event.getFormattedMessage())
                .contains("routeTemplate=/api/v1/pins/{pinId}")
                .doesNotContain("/api/v1/pins/42");
        assertThat(event.getThrowableProxy().getClassName())
                .isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void 라우트_템플릿을_확인할_수_없으면_원본_URL을_사용하지_않는다() {
        MockHttpServletRequest request = request("/api/v1/pins/42");

        HttpErrorLogger.error(
                request,
                GeneralErrorCode.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다.",
                new IllegalStateException("unexpected error")
        );

        ILoggingEvent event = appender.list.getFirst();

        assertThat(keyValues(event))
                .containsEntry("routeTemplate", "<unresolved-route>");
        assertThat(event.getFormattedMessage())
                .contains("routeTemplate=<unresolved-route>")
                .doesNotContain("/api/v1/pins/42");
    }

    @Test
    void 유효한_Cloud_Trace_헤더는_추적_필드로_기록한다() {
        MockHttpServletRequest request = request("/api/v1/pins");
        request.addHeader("X-Cloud-Trace-Context", TRACE_ID.toUpperCase() + "/123;o=1");

        HttpErrorLogger.error(
                request,
                GeneralErrorCode.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다.",
                new IllegalStateException("unexpected error")
        );

        Map<String, Object> fields = keyValues(appender.list.getFirst());
        assertThat(fields).containsEntry("traceId", TRACE_ID);
        assertThat((String) fields.get("logging.googleapis.com/trace"))
                .startsWith("projects/")
                .endsWith("/traces/" + TRACE_ID);
    }

    @Test
    void 잘못된_Cloud_Trace_헤더는_구조화_필드에서_제외한다() {
        MockHttpServletRequest request = request("/api/v1/pins");
        request.addHeader("X-Cloud-Trace-Context", "invalid-trace");

        HttpErrorLogger.error(
                request,
                GeneralErrorCode.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다.",
                new IllegalStateException("unexpected error")
        );

        assertThat(keyValues(appender.list.getFirst()))
                .doesNotContainKeys("traceId", "logging.googleapis.com/trace");
    }

    private MockHttpServletRequest request(String requestUri) {
        return new MockHttpServletRequest("GET", requestUri);
    }

    private Map<String, Object> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
