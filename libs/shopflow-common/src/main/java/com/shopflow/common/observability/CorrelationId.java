package com.shopflow.common.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One correlation id follows a single logical operation across every service and every log line.
 *
 * <p>The gateway generates it (or accepts a client-supplied one), forwards it downstream in the
 * {@code X-Correlation-Id} header, and copies it into every Kafka {@link
 * com.shopflow.common.event.EventEnvelope}. Each service places it in the SLF4J {@link MDC} so the
 * logging pattern can print {@code [<correlationId>]} on every line. To debug a failed order you
 * paste one id into a log query and see the whole cross-service timeline.
 *
 * <p>This class is framework-agnostic on purpose: the servlet filter, a future reactive filter, and
 * the Kafka consumer interceptor all share it.
 */
public final class CorrelationId {

    /** HTTP header carrying the id between the gateway and the services. */
    public static final String HEADER = "X-Correlation-Id";

    /** Key under which the id is stored in the SLF4J MDC (referenced by the logback pattern). */
    public static final String MDC_KEY = "correlationId";

    /** Accept only sane values from clients; anything else is replaced with a fresh id. */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private CorrelationId() {
    }

    /** Return {@code candidate} if it is a safe, non-blank value, otherwise a new random UUID string. */
    public static String sanitizeOrCreate(String candidate) {
        if (candidate != null && SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    /** Put the id into the MDC for the current thread. */
    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    /** Current thread's correlation id, or {@code null} if none is bound. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Remove the id from the MDC. Always call this in a {@code finally} block. */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
