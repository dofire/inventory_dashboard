package com.dofire.sv_vehicle.global.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpanLoggingHandlerTests {
    @Test
    void logsChildIdsWithPrefixAndRestoresParentMdc() {
        Logger logger = (Logger) LoggerFactory.getLogger(SpanLoggingHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        var previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("traceId", "parent-trace");
            MDC.put("spanId", "parent-span");
            Span child = mock(Span.class);
            TraceContext ids = mock(TraceContext.class);
            when(child.context()).thenReturn(ids);
            when(ids.traceId()).thenReturn("child-trace");
            when(ids.spanId()).thenReturn("child-span");
            TracingContext tracing = new TracingContext();
            tracing.setSpan(child);
            Observation.Context context = new Observation.Context();
            context.setName("taskA");
            context.put(TracingContext.class, tracing);
            SpanLoggingHandler handler = new SpanLoggingHandler();
            handler.onScopeOpened(context);
            handler.onScopeOpened(context);
            context.setError(new RuntimeException("secret user input"));
            handler.onStop(context);
            assertEquals(2, appender.list.size());
            assertTrue(appender.list.getFirst().getFormattedMessage().contains("Starting taskA."));
            assertTrue(appender.list.getLast().getFormattedMessage().contains("Could not complete taskA."));
            for (var event : appender.list) {
                assertTrue(event.getFormattedMessage().startsWith("<> "));
                assertFalse(event.getFormattedMessage().contains("secret"));
                assertEquals("child-trace", event.getMDCPropertyMap().get("traceId"));
                assertEquals("child-span", event.getMDCPropertyMap().get("spanId"));
            }
            assertEquals("parent-trace", MDC.get("traceId"));
            assertEquals("parent-span", MDC.get("spanId"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
    }
}
