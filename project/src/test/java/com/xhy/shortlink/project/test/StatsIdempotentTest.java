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

package com.xhy.shortlink.project.test;

import com.xhy.shortlink.project.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.project.dao.mapper.*;
import com.xhy.shortlink.project.mq.consumer.RocketMQ.ShortLinkStatsSaveRocketMQConsumer;
import com.xhy.shortlink.project.mq.event.ShortLinkStatsRecordEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;

public class StatsIdempotentTest extends BaseIdempotentTest {

    @Autowired
    private ShortLinkStatsSaveRocketMQConsumer statsConsumer;

    // 🔥 Mock 这一堆 Mapper，因为我们不关心数据对不对，只关心它们被调了几次
    @MockBean private ShortLinkMapper shortLinkMapper;
    @MockBean private ShortLinkColdMapper shortLinkColdMapper;
    @MockBean private ShortLinkGoToMapper shortLinkGoToMapper;
    @MockBean private LinkAccessStatsMapper linkAccessStatsMapper;
    @MockBean private LinkLocaleStatsMapper linkLocaleStatsMapper;
    @MockBean private LinkOsStatsMapper linkOsStatsMapper;
    @MockBean private LinkBrowserStatsMapper linkBrowserStatsMapper;
    @MockBean private LinkAccessLogsMapper linkAccessLogsMapper;
    @MockBean private LinkDeviceStatsMapper linkDeviceStatsMapper;
    @MockBean private LinkNetworkStatsMapper linkNetworkStatsMapper;

    @BeforeEach
    public void setupMocks() {
        // 模拟查 GID 的动作，防止空指针
        ShortLinkGoToDO mockGoto = new ShortLinkGoToDO();
        mockGoto.setGid("test_gid");
        Mockito.when(shortLinkGoToMapper.selectOne(any())).thenReturn(mockGoto);
        Mockito.when(shortLinkMapper.incrementStats(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(1);
    }

    @Test
    public void testStatsIncrementIdempotency() {
        String messageId = UUID.randomUUID().toString();
        cleanRedis(messageId);

        ShortLinkStatsRecordEvent event = ShortLinkStatsRecordEvent.builder()
                .eventId(messageId)
                .fullShortUrl("nurl.ink/statsTest")
                .remoteAddr("127.0.0.1")
                .currentDate(new Date())
                .build();

        // --- 第一次调用 ---
        System.out.println(">>> 第 1 次统计 PV");
        statsConsumer.onMessage(event);

        // --- 第二次调用 ---
        System.out.println(">>> 第 2 次统计 PV (应该被拦截)");
        statsConsumer.onMessage(event);

        // 🔍 核心验证：
        // 验证 incrementStats (PV+1) 只执行了 1 次
        Mockito.verify(shortLinkMapper, Mockito.times(1))
                .incrementStats(
                        eq("test_gid"),
                        eq("nurl.ink/statsTest"),
                        eq(1),
                        eq(1),
                        eq(1)
                );

        // 验证其中一个插入操作也只执行了 1 次
        Mockito.verify(linkAccessStatsMapper, Mockito.times(1)).shortLinkStats(any());

        Assertions.assertTrue(idempotentHandler.isAccomplish(messageId));
        System.out.println("✅ 监控统计幂等测试通过");
    }
}