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

package com.xhy.shortlink.biz.statsservice.mq.consumer;

import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessLogsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkOsStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessLogsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkBrowserStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkDeviceStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkLocaleStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkNetworkStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkOsStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.statsservice.locale.AccessLocaleEnricher;
import com.xhy.shortlink.biz.statsservice.locale.IpLocation;
import com.xhy.shortlink.biz.statsservice.locale.IpLocationService;
import com.xhy.shortlink.biz.statsservice.metrics.StatsMetrics;
import com.xhy.shortlink.biz.statsservice.mq.event.ShortLinkStatsRecordEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkStatsSaveConsumerTest {

    @InjectMocks
    private ShortLinkStatsSaveConsumer consumer;

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private ShortLinkGoToMapper shortLinkGoToMapper;
    @Mock
    private ShortLinkColdMapper shortLinkColdMapper;
    @Mock
    private LinkAccessStatsMapper linkAccessStatsMapper;
    @Mock
    private LinkOsStatsMapper linkOsStatsMapper;
    @Mock
    private LinkBrowserStatsMapper linkBrowserStatsMapper;
    @Mock
    private LinkAccessLogsMapper linkAccessLogsMapper;
    @Mock
    private LinkDeviceStatsMapper linkDeviceStatsMapper;
    @Mock
    private LinkNetworkStatsMapper linkNetworkStatsMapper;
    @Mock
    private LinkLocaleStatsMapper linkLocaleStatsMapper;
    @Mock
    private StatsMetrics statsMetrics;
    @Mock
    private IpLocationService ipLocationService;
    @Mock
    private AccessLocaleEnricher accessLocaleEnricher;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        lenient().when(ipLocationService.peekWithoutHttp(any())).thenReturn(Optional.of(IpLocation.unknown()));
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        List<Object> pipeline = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            pipeline.add(1L);
        }
        lenient().when(stringRedisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(pipeline);
        RReadWriteLock rwLock = mock(RReadWriteLock.class);
        RLock readLock = mock(RLock.class);
        lenient().when(redissonClient.getReadWriteLock(anyString())).thenReturn(rwLock);
        lenient().when(rwLock.readLock()).thenReturn(readLock);
    }

    @Test
    void onMessage_withGid_savesStats() {
        consumer.onMessage(event("evt-1", "test.cn/abc", "g1", "127.0.0.1"));

        ArgumentCaptor<List<LinkAccessLogsDO>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkAccessLogsMapper).insertBatch(logCaptor.capture());
        assertEquals("中国-未知-未知", logCaptor.getValue().get(0).getLocale());
        verify(linkOsStatsMapper).shortLinkOsStateBatch(anyList());
        verify(linkBrowserStatsMapper).shortLinkBrowserStateBatch(anyList());
        verify(linkDeviceStatsMapper).shortLinkDeviceStateBatch(anyList());
        verify(linkNetworkStatsMapper).shortLinkNetworkStateBatch(anyList());
        verify(linkAccessStatsMapper).shortLinkStats(anyList());
        InOrder order = inOrder(shortLinkMapper, linkLocaleStatsMapper);
        order.verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/abc"), eq(1), anyInt(), anyInt());
        order.verify(linkLocaleStatsMapper).shortLinkLocaleStateBatch(anyList());
        verify(accessLocaleEnricher, never()).submit(any(), any(), any(), any());
        verify(statsMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    void onMessage_withoutGid_queriesGoToTable() {
        ShortLinkGoToDO goTo = new ShortLinkGoToDO();
        goTo.setGid("g2");
        when(shortLinkGoToMapper.selectOne(any())).thenReturn(goTo);

        consumer.onMessage(event("evt-2", "test.cn/xyz", null, "10.0.0.1"));

        verify(shortLinkGoToMapper).selectOne(any());
        verify(shortLinkMapper).incrementStats(eq("g2"), eq("test.cn/xyz"), eq(1), anyInt(), anyInt());
        verify(statsMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    void onMessage_hotTableMiss_fallbackToCold() {
        when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(0);

        consumer.onMessage(event("evt-3", "test.cn/cold", "g3", "192.168.1.1"));

        verify(shortLinkColdMapper).incrementStats(eq("g3"), eq("test.cn/cold"), eq(1), anyInt(), anyInt());
        verify(statsMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    void onMessage_runtimeException_recordsFailure() {
        when(redissonClient.getReadWriteLock(anyString())).thenThrow(new RuntimeException("lock error"));

        try {
            consumer.onMessage(event("evt-4", "test.cn/error", "g1", "127.0.0.1"));
        } catch (RuntimeException ignored) {
        }

        verify(statsMetrics).recordConsumeFailure(any(Duration.class));
        verify(stringRedisTemplate).delete(anyList());
    }

    @Test
    void onMessage_publicIp_defersAmapUntilAfterPersist() {
        when(ipLocationService.peekWithoutHttp("114.114.114.114")).thenReturn(Optional.empty());
        when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(1);

        consumer.onMessage(event("evt-5", "test.cn/pub", "g1", "114.114.114.114"));

        ArgumentCaptor<List<LinkAccessLogsDO>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkAccessLogsMapper).insertBatch(logCaptor.capture());
        assertEquals("中国-未知-未知", logCaptor.getValue().get(0).getLocale());
        InOrder order = inOrder(shortLinkMapper, accessLocaleEnricher);
        order.verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/pub"), eq(1), anyInt(), anyInt());
        order.verify(accessLocaleEnricher).submit(eq("test.cn/pub"), any(), eq("114.114.114.114"), eq(null));
        verify(linkLocaleStatsMapper, never()).shortLinkLocaleStateBatch(anyList());
        verify(statsMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    void onMessage_incrementStatsFails_doesNotSubmitLocaleEnrich() {
        when(ipLocationService.peekWithoutHttp("114.114.114.114")).thenReturn(Optional.empty());
        when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        try {
            consumer.onMessage(event("evt-6", "test.cn/fail", "g1", "114.114.114.114"));
        } catch (RuntimeException ignored) {
        }

        verify(accessLocaleEnricher, never()).submit(any(), any(), any(), any());
        verify(statsMetrics).recordConsumeFailure(any(Duration.class));
    }

    @Test
    void consumeBatch_sameUrl_aggregatesPvAndOs() {
        when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(1);
        Date now = new Date();
        consumer.consumeBatch(List.of(
                event("b1", "test.cn/agg", "g1", "127.0.0.1", now),
                event("b2", "test.cn/agg", "g1", "127.0.0.1", now)));

        verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/agg"), eq(2), anyInt(), anyInt());
        ArgumentCaptor<List<LinkOsStatsDO>> osCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkOsStatsMapper).shortLinkOsStateBatch(osCaptor.capture());
        assertEquals(1, osCaptor.getValue().size());
        assertEquals(2, osCaptor.getValue().get(0).getCnt());
        ArgumentCaptor<List<LinkAccessLogsDO>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkAccessLogsMapper).insertBatch(logCaptor.capture());
        assertEquals(2, logCaptor.getValue().size());
    }

    private static ShortLinkStatsRecordEvent event(String id, String url, String gid, String ip) {
        return event(id, url, gid, ip, new Date());
    }

    private static ShortLinkStatsRecordEvent event(String id, String url, String gid, String ip, Date date) {
        return ShortLinkStatsRecordEvent.builder()
                .eventId(id)
                .fullShortUrl(url)
                .gid(gid)
                .remoteAddr(ip)
                .os("Windows")
                .browser("Chrome")
                .device("PC")
                .network("WIFI")
                .uv("uv-" + id)
                .currentDate(date)
                .build();
    }
}
