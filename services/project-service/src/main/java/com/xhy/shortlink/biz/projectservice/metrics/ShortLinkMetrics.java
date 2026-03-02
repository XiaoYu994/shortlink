package com.xhy.shortlink.biz.projectservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ShortLinkMetrics {

    private final Counter createSuccessCounter;
    private final Counter createFailureCounter;
    private final Counter redirectSuccessCounter;
    private final Counter redirectFailureCounter;
    private final Counter coldMigrationBatchCounter;
    private final DistributionSummary coldMigrationItemSummary;
    private final Timer redirectLatencyTimer;

    public ShortLinkMetrics(MeterRegistry meterRegistry) {
        this.createSuccessCounter = Counter.builder("shortlink_create_success_total")
                .description("Total successful short-link creations")
                .register(meterRegistry);
        this.createFailureCounter = Counter.builder("shortlink_create_failure_total")
                .description("Total failed short-link creations")
                .register(meterRegistry);
        this.redirectSuccessCounter = Counter.builder("shortlink_redirect_success_total")
                .description("Total successful short-link redirects")
                .register(meterRegistry);
        this.redirectFailureCounter = Counter.builder("shortlink_redirect_failure_total")
                .description("Total failed short-link redirects")
                .register(meterRegistry);
        this.coldMigrationBatchCounter = Counter.builder("shortlink_cold_migration_batch_total")
                .description("Total cold migration batches")
                .register(meterRegistry);
        this.coldMigrationItemSummary = DistributionSummary.builder("shortlink_cold_migration_items")
                .description("Number of migrated items in each cold migration batch")
                .register(meterRegistry);
        this.redirectLatencyTimer = Timer.builder("shortlink_redirect_latency")
                .description("Redirect latency")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void recordCreateSuccess() {
        createSuccessCounter.increment();
    }

    public void recordCreateFailure() {
        createFailureCounter.increment();
    }

    public void recordRedirectSuccess(Duration duration) {
        redirectSuccessCounter.increment();
        redirectLatencyTimer.record(duration);
    }

    public void recordRedirectFailure(Duration duration) {
        redirectFailureCounter.increment();
        redirectLatencyTimer.record(duration);
    }

    public void recordColdMigrationBatch(int size) {
        coldMigrationBatchCounter.increment();
        coldMigrationItemSummary.record(size);
    }
}
