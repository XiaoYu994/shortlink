package com.xhy.shortlink.biz.statsservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsMetricsTest {

    @Test
    void shouldIncrementMqConsumeSuccessCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        StatsMetrics metrics = new StatsMetrics(registry);

        metrics.recordConsumeSuccess(Duration.ofMillis(30));

        double count = registry.get("shortlink_mq_consume_success_total").counter().count();
        assertEquals(1.0, count, 0.0001);
    }

    @Test
    void shouldIncrementMqConsumeFailureCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        StatsMetrics metrics = new StatsMetrics(registry);

        metrics.recordConsumeFailure(Duration.ofMillis(45));

        double count = registry.get("shortlink_mq_consume_failure_total").counter().count();
        assertEquals(1.0, count, 0.0001);
    }

    @Test
    void shouldRecordMqConsumeLatency() {
        MeterRegistry registry = new SimpleMeterRegistry();
        StatsMetrics metrics = new StatsMetrics(registry);

        metrics.recordConsumeSuccess(Duration.ofMillis(10));

        double count = registry.get("shortlink_mq_consume_latency").timer().count();
        assertEquals(1.0, count, 0.0001);
    }
}
