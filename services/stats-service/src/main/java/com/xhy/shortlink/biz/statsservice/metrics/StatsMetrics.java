/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
