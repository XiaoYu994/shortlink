package com.xhy.shortlink.biz.projectservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShortLinkMetricsTest {

    @Test
    void shouldIncrementCreateSuccessCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        ShortLinkMetrics metrics = new ShortLinkMetrics(registry);

        metrics.recordCreateSuccess();

        double count = registry.get("shortlink_create_success_total").counter().count();
        assertEquals(1.0, count, 0.0001);
    }

    @Test
    void shouldIncrementRedirectErrorCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        ShortLinkMetrics metrics = new ShortLinkMetrics(registry);

        metrics.recordRedirectFailure(Duration.ofMillis(120));

        double count = registry.get("shortlink_redirect_failure_total").counter().count();
        assertEquals(1.0, count, 0.0001);
    }

    @Test
    void shouldRecordRedirectLatencyTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();
        ShortLinkMetrics metrics = new ShortLinkMetrics(registry);

        metrics.recordRedirectSuccess(Duration.ofMillis(80));

        double count = registry.get("shortlink_redirect_latency").timer().count();
        assertEquals(1.0, count, 0.0001);
    }
}
