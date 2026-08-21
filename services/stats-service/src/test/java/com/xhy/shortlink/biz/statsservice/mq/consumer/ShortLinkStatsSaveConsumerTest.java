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
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessStatsDO;
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
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import static org.mockito.Mockito.times;
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
        stubPipeline(1L, 1L, 1L, 1L);
        lenient().when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(1);
        AtomicLong ids = new AtomicLong(100);
        lenient().when(linkAccessLogsMapper.insertBatch(anyList())).thenAnswer(invocation -> {
            List<LinkAccessLogsDO> logs = invocation.getArgument(0);
            for (LinkAccessLogsDO log : logs) {
                log.setId(ids.getAndIncrement());
            }
            return logs.size();
        });
        RReadWriteLock rwLock = mock(RReadWriteLock.class);
        RLock readLock = mock(RLock.class);
        lenient().when(redissonClient.getReadWriteLock(anyString())).thenReturn(rwLock);
        lenient().when(rwLock.readLock()).thenReturn(readLock);
    }

    @SuppressWarnings("unchecked")
    private void stubPipeline(long todayUv, long totalUv, long todayUip, long totalUip) {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        lenient().when(stringRedisTemplate.executePipelined(any(SessionCallback.class))).thenAnswer(invocation -> {
            int kind = calls.getAndIncrement() % 3;
            if (kind == 0) {
                return firstPipeline(8, todayUv, totalUv, todayUip, totalUip);
            }
            if (kind == 1) {
                return List.of("2", "2");
            }
            return List.of("1", "1", "1", "1");
        });
    }

    private static List<Object> firstPipeline(int eventCount, long todayUv, long totalUv, long todayUip, long totalUip) {
        List<Object> out = new ArrayList<>();
        out.add("1.0");
        out.add("1");
        for (int i = 0; i < eventCount; i++) {
            out.add(String.valueOf(todayUv));
            out.add(String.valueOf(totalUv));
            out.add(String.valueOf(todayUip));
            out.add(String.valueOf(totalUip));
        }
        out.add("1");
        out.add("1");
        return out;
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
        order.verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/abc"), eq(1), eq(1), eq(1));
        order.verify(linkLocaleStatsMapper).shortLinkLocaleStateBatch(anyList());
        verify(accessLocaleEnricher, never()).submit(any(), any(), any(), any());
        verify(statsMetrics).recordConsumeSuccess(eq(1), any(Duration.class));
        ArgumentCaptor<List<LinkAccessStatsDO>> statsCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkAccessStatsMapper).shortLinkStats(statsCaptor.capture());
        assertEquals(1, statsCaptor.getValue().get(0).getUv());
        assertEquals(1, statsCaptor.getValue().get(0).getUip());
    }

    @Test
    void onMessage_withoutGid_queriesGoToTable() {
        ShortLinkGoToDO goTo = new ShortLinkGoToDO();
        goTo.setGid("g2");
        when(shortLinkGoToMapper.selectOne(any())).thenReturn(goTo);

        consumer.onMessage(event("evt-2", "test.cn/xyz", null, "10.0.0.1"));

        verify(shortLinkGoToMapper).selectOne(any());
        verify(shortLinkMapper).incrementStats(eq("g2"), eq("test.cn/xyz"), eq(1), eq(1), eq(1));
        verify(statsMetrics).recordConsumeSuccess(eq(1), any(Duration.class));
    }

    @Test
    void onMessage_hotTableMiss_fallbackToCold() {
        when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(0);

        consumer.onMessage(event("evt-3", "test.cn/cold", "g3", "192.168.1.1"));

        verify(shortLinkColdMapper).incrementStats(eq("g3"), eq("test.cn/cold"), eq(1), eq(1), eq(1));
        verify(statsMetrics).recordConsumeSuccess(eq(1), any(Duration.class));
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
        order.verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/pub"), eq(1), eq(1), eq(1));
        order.verify(accessLocaleEnricher).submit(eq("test.cn/pub"), any(), eq("114.114.114.114"), eq(100L));
        verify(linkLocaleStatsMapper, never()).shortLinkLocaleStateBatch(anyList());
        verify(statsMetrics).recordConsumeSuccess(eq(1), any(Duration.class));
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

        verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/agg"), eq(2), eq(2), eq(2));
        ArgumentCaptor<List<LinkOsStatsDO>> osCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkOsStatsMapper).shortLinkOsStateBatch(osCaptor.capture());
        assertEquals(1, osCaptor.getValue().size());
        assertEquals(2, osCaptor.getValue().get(0).getCnt());
        ArgumentCaptor<List<LinkAccessLogsDO>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkAccessLogsMapper).insertBatch(logCaptor.capture());
        assertEquals(2, logCaptor.getValue().size());
        ArgumentCaptor<List<LinkAccessStatsDO>> statsCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkAccessStatsMapper).shortLinkStats(statsCaptor.capture());
        assertEquals(2, statsCaptor.getValue().get(0).getPv());
        assertEquals(2, statsCaptor.getValue().get(0).getUv());
        assertEquals(2, statsCaptor.getValue().get(0).getUip());
        verify(statsMetrics).recordConsumeSuccess(eq(2), any(Duration.class));
    }

    @Test
    void consumeBatch_returningVisitor_usesTotalHllForLinkUv() {
        stubPipeline(1L, 0L, 1L, 0L);
        Date now = new Date();
        consumer.consumeBatch(List.of(
                event("r1", "test.cn/ret", "g1", "127.0.0.1", now),
                event("r2", "test.cn/ret", "g1", "10.0.0.2", now)));

        verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/ret"), eq(2), eq(0), eq(0));
        ArgumentCaptor<List<LinkAccessStatsDO>> statsCaptor = ArgumentCaptor.forClass(List.class);
        verify(linkAccessStatsMapper).shortLinkStats(statsCaptor.capture());
        assertEquals(2, statsCaptor.getValue().get(0).getUv());
        assertEquals(2, statsCaptor.getValue().get(0).getUip());
    }

    @Test
    void consumeBatch_secondUrlFails_doesNotReleaseStartedUrlClaims() {
        when(shortLinkMapper.incrementStats(anyString(), eq("test.cn/a"), anyInt(), anyInt(), anyInt()))
                .thenReturn(1);
        when(shortLinkMapper.incrementStats(anyString(), eq("test.cn/b"), anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        try {
            consumer.consumeBatch(List.of(
                    event("a1", "test.cn/a", "g1", "127.0.0.1"),
                    event("b1", "test.cn/b", "g1", "127.0.0.1")));
        } catch (RuntimeException ignored) {
        }

        verify(stringRedisTemplate, never()).delete(anyList());
        verify(stringRedisTemplate, never()).delete(any(Collection.class));
        verify(linkAccessLogsMapper, times(2)).insertBatch(anyList());
        verify(statsMetrics).recordConsumeFailure(any(Duration.class));
    }

    @Test
    void consumeBatch_missingGid_skipsLinkIncrement() {
        when(shortLinkGoToMapper.selectOne(any())).thenReturn(null);

        consumer.consumeBatch(List.of(event("m1", "test.cn/miss", null, "127.0.0.1")));

        verify(shortLinkMapper, never()).incrementStats(any(), any(), anyInt(), anyInt(), anyInt());
        verify(shortLinkColdMapper, never()).incrementStats(any(), any(), anyInt(), anyInt(), anyInt());
        verify(linkAccessLogsMapper).insertBatch(anyList());
        verify(statsMetrics).recordConsumeSuccess(eq(1), any(Duration.class));
    }

    @Test
    void consumeBatch_usesNonBlankGidFromLaterEvent() {
        Date now = new Date();

        consumer.consumeBatch(List.of(
                event("gid-1", "test.cn/gid", null, "127.0.0.1", now),
                event("gid-2", "test.cn/gid", "g2", "10.0.0.2", now)));

        verify(shortLinkGoToMapper, never()).selectOne(any());
        verify(shortLinkMapper).incrementStats(eq("g2"), eq("test.cn/gid"), eq(2), eq(2), eq(2));
    }

    @Test
    void handleMessages_badJson_skipsPoisonAndConsumesValid() {
        MessageExt bad = new MessageExt();
        bad.setMsgId("bad");
        bad.setBody("{".getBytes(StandardCharsets.UTF_8));
        MessageExt empty = new MessageExt();
        empty.setMsgId("empty");
        empty.setBody(new byte[0]);
        MessageExt good = new MessageExt();
        good.setMsgId("good");
        good.setBody("""
                {"eventId":"g1","fullShortUrl":"test.cn/ok","gid":"g1","remoteAddr":"127.0.0.1",\
                "os":"Windows","browser":"Chrome","device":"PC","network":"WIFI","uv":"uv-g1"}
                """.getBytes(StandardCharsets.UTF_8));

        ConsumeConcurrentlyStatus status = consumer.handleMessages(List.of(bad, empty, good));

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, status);
        verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/ok"), eq(1), eq(1), eq(1));
        verify(statsMetrics).recordConsumeSuccess(eq(1), any(Duration.class));
    }

    @Test
    void handleMessages_withoutEventId_usesRocketMqMessageIdForIdempotency() {
        MessageExt message = new MessageExt();
        message.setMsgId("mq-1");
        message.setBody("""
                {"fullShortUrl":"test.cn/no-id","gid":"g1","remoteAddr":"127.0.0.1",
                "os":"Windows","browser":"Chrome","device":"PC","network":"WIFI","uv":"uv-1"}
                """.getBytes(StandardCharsets.UTF_8));

        consumer.handleMessages(List.of(message));

        verify(valueOperations).setIfAbsent(eq("stats-save:mq-1"), eq("1"), anyLong(), any());
    }

    @Test
    void onMessage_publicIpWithoutDate_usesPersistedAccessDateForEnrichment() {
        when(ipLocationService.peekWithoutHttp("114.114.114.114")).thenReturn(Optional.empty());
        ShortLinkStatsRecordEvent event = event("evt-no-date", "test.cn/no-date", "g1", "114.114.114.114");
        event.setCurrentDate(null);

        consumer.onMessage(event);

        ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
        verify(accessLocaleEnricher).submit(eq("test.cn/no-date"), dateCaptor.capture(),
                eq("114.114.114.114"), any());
        assertNotNull(dateCaptor.getValue());
        assertEquals(event.getCurrentDate(), dateCaptor.getValue());
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
