package com.xhy.shortlink.biz.statsservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class StatsMetrics {

    private final Counter consumeSuccessCounter;
    private final Counter consumeFailureCounter;
    private final Timer consumeLatencyTimer;

    public StatsMetrics(MeterRegistry meterRegistry) {
        this.consumeSuccessCounter = Counter.builder("shortlink_mq_consume_success_total")
                .description("Total successful MQ consumptions")
                .register(meterRegistry);
        this.consumeFailureCounter = Counter.builder("shortlink_mq_consume_failure_total")
                .description("Total failed MQ consumptions")
                .register(meterRegistry);
        this.consumeLatencyTimer = Timer.builder("shortlink_mq_consume_latency")
                .description("MQ consume latency")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void recordConsumeSuccess(Duration duration) {
        consumeSuccessCounter.increment();
        consumeLatencyTimer.record(duration);
    }

    public void recordConsumeFailure(Duration duration) {
        consumeFailureCounter.increment();
        consumeLatencyTimer.record(duration);
    }
}
