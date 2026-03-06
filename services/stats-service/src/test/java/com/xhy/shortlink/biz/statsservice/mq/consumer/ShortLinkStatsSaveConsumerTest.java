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

import com.xhy.shortlink.biz.statsservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.statsservice.dao.mapper.*;
import com.xhy.shortlink.biz.statsservice.metrics.StatsMetrics;
import com.xhy.shortlink.biz.statsservice.mq.event.ShortLinkStatsRecordEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private LinkLocaleStatsMapper linkLocaleStatsMapper;
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
    private StatsMetrics statsMetrics;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "statsLocaleAmapKey", "test-amap-key");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_withGid_savesStats() {
        ShortLinkStatsRecordEvent event = ShortLinkStatsRecordEvent.builder()
                .eventId("evt-1")
                .fullShortUrl("test.cn/abc")
                .gid("g1")
                .remoteAddr("127.0.0.1")
                .os("Windows")
                .browser("Chrome")
                .device("PC")
                .network("WIFI")
                .uv("uv-123")
                .currentDate(new Date())
                .build();

        RReadWriteLock rwLock = mock(RReadWriteLock.class);
        RLock readLock = mock(RLock.class);
        when(redissonClient.getReadWriteLock(anyString())).thenReturn(rwLock);
        when(rwLock.readLock()).thenReturn(readLock);

        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(stringRedisTemplate.getExpire(anyString())).thenReturn(-1L);

        HyperLogLogOperations<String, String> hllOps = mock(HyperLogLogOperations.class);
        when(stringRedisTemplate.opsForHyperLogLog()).thenReturn(hllOps);
        when(hllOps.add(anyString(), anyString())).thenReturn(1L);
        when(hllOps.size(anyString())).thenReturn(1L);

        when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(1);

        consumer.onMessage(event);

        verify(linkAccessStatsMapper).shortLinkStats(anyList());
        verify(linkOsStatsMapper).shortLinkOsState(any());
        verify(linkBrowserStatsMapper).shortLinkBrowserState(any());
        verify(linkDeviceStatsMapper).shortLinkDeviceState(any());
        verify(linkNetworkStatsMapper).shortLinkNetworkState(any());
        verify(linkAccessLogsMapper).insert(any(com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessLogsDO.class));
        verify(linkLocaleStatsMapper).shortLinkLocaleState(any());
        verify(shortLinkMapper).incrementStats(eq("g1"), eq("test.cn/abc"), eq(1), anyInt(), anyInt());
        verify(statsMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_withoutGid_queriesGoToTable() {
        ShortLinkStatsRecordEvent event = ShortLinkStatsRecordEvent.builder()
                .eventId("evt-2")
                .fullShortUrl("test.cn/xyz")
                .gid(null)
                .remoteAddr("10.0.0.1")
                .os("iOS")
                .browser("Safari")
                .device("Mobile")
                .network("Mobile")
                .uv("uv-456")
                .currentDate(new Date())
                .build();

        RReadWriteLock rwLock = mock(RReadWriteLock.class);
        RLock readLock = mock(RLock.class);
        when(redissonClient.getReadWriteLock(anyString())).thenReturn(rwLock);
        when(rwLock.readLock()).thenReturn(readLock);

        ShortLinkGoToDO goTo = new ShortLinkGoToDO();
        goTo.setGid("g2");
        when(shortLinkGoToMapper.selectOne(any())).thenReturn(goTo);

        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(stringRedisTemplate.getExpire(anyString())).thenReturn(-1L);

        HyperLogLogOperations<String, String> hllOps = mock(HyperLogLogOperations.class);
        when(stringRedisTemplate.opsForHyperLogLog()).thenReturn(hllOps);
        when(hllOps.add(anyString(), anyString())).thenReturn(1L);
        when(hllOps.size(anyString())).thenReturn(1L);

        when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(1);

        consumer.onMessage(event);

        verify(shortLinkGoToMapper).selectOne(any());
        verify(shortLinkMapper).incrementStats(eq("g2"), eq("test.cn/xyz"), eq(1), anyInt(), anyInt());
        verify(statsMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_hotTableMiss_fallbackToCold() {
        ShortLinkStatsRecordEvent event = ShortLinkStatsRecordEvent.builder()
                .eventId("evt-3")
                .fullShortUrl("test.cn/cold")
                .gid("g3")
                .remoteAddr("192.168.1.1")
                .os("Android")
                .browser("Chrome")
                .device("Mobile")
                .network("WIFI")
                .uv("uv-789")
                .currentDate(new Date())
                .build();

        RReadWriteLock rwLock = mock(RReadWriteLock.class);
        RLock readLock = mock(RLock.class);
        when(redissonClient.getReadWriteLock(anyString())).thenReturn(rwLock);
        when(rwLock.readLock()).thenReturn(readLock);

        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(stringRedisTemplate.getExpire(anyString())).thenReturn(-1L);

        HyperLogLogOperations<String, String> hllOps = mock(HyperLogLogOperations.class);
        when(stringRedisTemplate.opsForHyperLogLog()).thenReturn(hllOps);
        when(hllOps.add(anyString(), anyString())).thenReturn(1L);
        when(hllOps.size(anyString())).thenReturn(1L);

        when(shortLinkMapper.incrementStats(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(0);

        consumer.onMessage(event);

        verify(shortLinkColdMapper).incrementStats(eq("g3"), eq("test.cn/cold"), eq(1), anyInt(), anyInt());
        verify(statsMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    void onMessage_runtimeException_recordsFailure() {
        ShortLinkStatsRecordEvent event = ShortLinkStatsRecordEvent.builder()
                .eventId("evt-4")
                .fullShortUrl("test.cn/error")
                .gid("g1")
                .currentDate(new Date())
                .build();

        when(redissonClient.getReadWriteLock(anyString())).thenThrow(new RuntimeException("lock error"));

        try {
            consumer.onMessage(event);
        } catch (RuntimeException ignored) {
        }

        verify(statsMetrics).recordConsumeFailure(any(Duration.class));
    }
}
