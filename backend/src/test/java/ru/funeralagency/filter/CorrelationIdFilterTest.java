package ru.funeralagency.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {
    @AfterEach
    void clearMdc() { MDC.clear(); }

    @Test
    void acceptsCorrectIdUsesMdcAndRestoresPreviousContextEvenOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "request-123._test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("correlationId", "previous");
        assertThrows(IOException.class, () -> new CorrelationIdFilter().doFilter(request, response, (req, res) -> {
            assertEquals("request-123._test", MDC.get("correlationId"));
            assertEquals("request-123._test", req.getAttribute("correlationId"));
            throw new IOException("test");
        }));
        assertEquals("request-123._test", response.getHeader(CorrelationIdFilter.HEADER));
        assertEquals("previous", MDC.get("correlationId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "with space", "line\nbreak", "кириллица", "!bad"})
    void invalidHeaderIsReplacedWithUuid(String id) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, id);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(request, response, (req, res) -> {});
        assertNotNull(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER)));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void lengthBoundaryAndMissingHeaderAreHandled() throws Exception {
        for (String id : new String[]{"x".repeat(64), "x".repeat(65), "x".repeat(10000)}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER, id);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new CorrelationIdFilter().doFilter(request, response, (req, res) -> {});
            if (id.length() == 64) {
                assertEquals(id, response.getHeader(CorrelationIdFilter.HEADER));
            } else {
                assertNotNull(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER)));
            }
        }
        var response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(new MockHttpServletRequest(), response, (req, res) -> {});
        assertNotNull(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER)));
    }

    @Test
    void httpLogHasCorrelationAndDoesNotContainCredentialsBodyOrQuery() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.addHeader(CorrelationIdFilter.HEADER, "log-test");
            request.addHeader("Authorization", "Bearer secret-access-token");
            request.setContent("secret-password".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            request.setQueryString("refreshToken=secret-refresh-token");
            new CorrelationIdFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
            assertEquals(1, appender.list.size());
            assertEquals("log-test", appender.list.get(0).getMDCPropertyMap().get("correlationId"));
            assertFalse(appender.list.get(0).getFormattedMessage().contains("secret"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
