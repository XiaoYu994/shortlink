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

package com.xhy.shortlink.biz.statsservice.config;

import com.xhy.shortlink.biz.statsservice.locale.AccessLocaleEnricher;
import com.xhy.shortlink.biz.statsservice.metrics.StatsMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 队列满时丢掉最旧任务并补一条「未知」地区行，新任务入队。
 * 不反压 MQ 消费线程。
 */
@Slf4j
public class AmapRejectedExecutionHandler implements RejectedExecutionHandler {

    private static final long WARN_INTERVAL_NANOS = 5_000_000_000L;

    private final StatsMetrics statsMetrics;
    private final AtomicLong lastWarnNanos = new AtomicLong();

    public AmapRejectedExecutionHandler(StatsMetrics statsMetrics) {
        this.statsMetrics = statsMetrics;
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            drop(runnable);
            return;
        }
        Runnable dropped = executor.getQueue().poll();
        drop(dropped);
        if (!executor.getQueue().offer(runnable)) {
            drop(runnable);
        }
    }

    private void drop(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        statsMetrics.recordLocaleEnrichDropped();
        if (runnable instanceof AccessLocaleEnricher.LocaleEnrichTask task) {
            try {
                task.persistUnknown();
            } catch (Exception ex) {
                log.warn("丢弃定位任务后补未知地区行失败", ex);
            }
        }
        long now = System.nanoTime();
        long previous = lastWarnNanos.get();
        if (now - previous > WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(previous, now)) {
            log.warn("amapExecutor 饱和，已丢弃地区补全任务");
        }
    }
}
