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
import com.xhy.shortlink.biz.statsservice.locale.IpLocationService;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessLogsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkLocaleStatsMapper;
import com.xhy.shortlink.biz.statsservice.metrics.StatsMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmapRejectedExecutionHandlerTest {

    @Mock
    private StatsMetrics statsMetrics;
    @Mock
    private IpLocationService ipLocationService;
    @Mock
    private LinkLocaleStatsMapper linkLocaleStatsMapper;
    @Mock
    private LinkAccessLogsMapper linkAccessLogsMapper;
    @Mock
    private Executor unusedExecutor;

    @Test
    void rejectedExecution_dropsOldestAndWritesUnknown() {
        AccessLocaleEnricher enricher = new AccessLocaleEnricher(
                ipLocationService, linkLocaleStatsMapper, linkAccessLogsMapper, unusedExecutor);
        AccessLocaleEnricher.LocaleEnrichTask oldest =
                enricher.new LocaleEnrichTask("a.cn/old", new Date(), "1.1.1.1", 1L);
        AccessLocaleEnricher.LocaleEnrichTask newest =
                enricher.new LocaleEnrichTask("a.cn/new", new Date(), "8.8.8.8", 2L);

        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(1);
        queue.offer(oldest);
        ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
        when(executor.isShutdown()).thenReturn(false);
        when(executor.getQueue()).thenReturn(queue);

        new AmapRejectedExecutionHandler(statsMetrics).rejectedExecution(newest, executor);

        verify(statsMetrics).recordLocaleEnrichDropped();
        verify(linkLocaleStatsMapper).shortLinkLocaleState(any());
        verify(ipLocationService, never()).resolveRemote(any());
    }
}
