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

package com.xhy.shortlink.biz.statsservice.locale;

import com.xhy.shortlink.biz.statsservice.dao.entity.LinkLocaleStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessLogsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkLocaleStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessLocaleEnricherTest {

    @Mock
    private IpLocationService ipLocationService;
    @Mock
    private LinkLocaleStatsMapper linkLocaleStatsMapper;
    @Mock
    private LinkAccessLogsMapper linkAccessLogsMapper;
    @Mock
    private Executor amapExecutor;

    private AccessLocaleEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new AccessLocaleEnricher(
                ipLocationService, linkLocaleStatsMapper, linkAccessLogsMapper, amapExecutor);
    }

    @Test
    void persistLocale_writesBuilderFields() {
        Date date = new Date();
        enricher.persistLocale("a.cn/x", date, new IpLocation("广东省", "深圳市", "440300"));
        ArgumentCaptor<LinkLocaleStatsDO> captor = ArgumentCaptor.forClass(LinkLocaleStatsDO.class);
        verify(linkLocaleStatsMapper).shortLinkLocaleState(captor.capture());
        LinkLocaleStatsDO row = captor.getValue();
        assertEquals("a.cn/x", row.getFullShortUrl());
        assertEquals("广东省", row.getProvince());
        assertEquals("深圳市", row.getCity());
        assertEquals("440300", row.getAdcode());
        assertEquals(1, row.getCnt());
        assertEquals("中国", row.getCountry());
        assertEquals(date, row.getDate());
    }

    @Test
    void run_resolvedProvince_updatesAccessLog() {
        when(ipLocationService.resolveRemote("8.8.8.8"))
                .thenReturn(new IpLocation("广东省", "深圳市", "440300"));
        AccessLocaleEnricher.LocaleEnrichTask task =
                enricher.new LocaleEnrichTask("a.cn/x", new Date(), "8.8.8.8", 7L);
        task.run();
        verify(linkLocaleStatsMapper).shortLinkLocaleState(any());
        verify(linkAccessLogsMapper).updateLocale(7L, "中国-广东省-深圳市");
    }

    @Test
    void run_unknownLocation_skipsAccessLogUpdate() {
        when(ipLocationService.resolveRemote("8.8.8.8")).thenReturn(IpLocation.unknown());
        AccessLocaleEnricher.LocaleEnrichTask task =
                enricher.new LocaleEnrichTask("a.cn/x", new Date(), "8.8.8.8", 7L);
        task.run();
        verify(linkLocaleStatsMapper).shortLinkLocaleState(any());
        verify(linkAccessLogsMapper, never()).updateLocale(anyLong(), anyString());
    }

    @Test
    void run_nullAccessLogId_skipsAccessLogUpdate() {
        when(ipLocationService.resolveRemote("8.8.8.8"))
                .thenReturn(new IpLocation("广东省", "深圳市", "440300"));
        AccessLocaleEnricher.LocaleEnrichTask task =
                enricher.new LocaleEnrichTask("a.cn/x", new Date(), "8.8.8.8", null);
        task.run();
        verify(linkAccessLogsMapper, never()).updateLocale(anyLong(), anyString());
    }

    @Test
    void run_resolveThrows_persistsUnknown() {
        when(ipLocationService.resolveRemote("8.8.8.8")).thenThrow(new RuntimeException("amap down"));
        AccessLocaleEnricher.LocaleEnrichTask task =
                enricher.new LocaleEnrichTask("a.cn/x", new Date(), "8.8.8.8", 7L);
        task.run();
        ArgumentCaptor<LinkLocaleStatsDO> captor = ArgumentCaptor.forClass(LinkLocaleStatsDO.class);
        verify(linkLocaleStatsMapper).shortLinkLocaleState(captor.capture());
        assertEquals("未知", captor.getValue().getProvince());
        verify(linkAccessLogsMapper, never()).updateLocale(anyLong(), anyString());
    }

    @Test
    void persistUnknown_onDiscard_writesUnknownRow() {
        AccessLocaleEnricher.LocaleEnrichTask task =
                enricher.new LocaleEnrichTask("a.cn/x", new Date(), "8.8.8.8", 7L);
        task.persistUnknown();
        ArgumentCaptor<LinkLocaleStatsDO> captor = ArgumentCaptor.forClass(LinkLocaleStatsDO.class);
        verify(linkLocaleStatsMapper).shortLinkLocaleState(captor.capture());
        assertEquals("未知", captor.getValue().getProvince());
    }

    @Test
    void submit_executesTask() {
        enricher.submit("a.cn/x", new Date(), "8.8.8.8", 1L);
        verify(amapExecutor).execute(any(AccessLocaleEnricher.LocaleEnrichTask.class));
    }

    @Test
    void run_updateLocaleThrows_persistsUnknown() {
        when(ipLocationService.resolveRemote("8.8.8.8"))
                .thenReturn(new IpLocation("广东省", "深圳市", "440300"));
        doThrow(new RuntimeException("db")).when(linkAccessLogsMapper).updateLocale(anyLong(), anyString());
        AccessLocaleEnricher.LocaleEnrichTask task =
                enricher.new LocaleEnrichTask("a.cn/x", new Date(), "8.8.8.8", 7L);
        task.run();
        ArgumentCaptor<LinkLocaleStatsDO> captor = ArgumentCaptor.forClass(LinkLocaleStatsDO.class);
        verify(linkLocaleStatsMapper, org.mockito.Mockito.times(2)).shortLinkLocaleState(captor.capture());
        assertEquals("未知", captor.getAllValues().get(1).getProvince());
    }
}
