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

package com.xhy.shortlink.biz.riskservice.mq.consumer;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xhy.shortlink.biz.riskservice.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.biz.riskservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.riskservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.riskservice.dto.resp.ShortLinkRiskCheckRespDTO;
import com.xhy.shortlink.biz.riskservice.metrics.RiskMetrics;
import com.xhy.shortlink.biz.riskservice.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.biz.riskservice.service.UrlRiskControlService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShortLinkRiskCheckConsumerTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ShortLinkDO.class);
    }

    @InjectMocks
    private ShortLinkRiskCheckConsumer consumer;

    @Mock
    private UrlRiskControlService riskControlService;
    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RiskMetrics riskMetrics;

    @Test
    void onMessage_safeUrl_noDisable() {
        ShortLinkRiskEvent event = ShortLinkRiskEvent.builder()
                .eventId("evt-1")
                .fullShortUrl("test.cn/abc")
                .originUrl("https://www.safe-site.com")
                .gid("g1")
                .userId(100L)
                .build();

        ShortLinkDO linkDO = new ShortLinkDO();
        linkDO.setEnableStatus(LinkEnableStatusEnum.ENABLE.getCode());
        when(shortLinkMapper.selectOne(any())).thenReturn(linkDO);

        when(riskControlService.checkUrlRisk("https://www.safe-site.com"))
                .thenReturn(ShortLinkRiskCheckRespDTO.builder()
                        .safe(true).riskType("NONE").summary("正常").detail("安全").build());

        consumer.onMessage(event);

        verify(shortLinkMapper, never()).update(any(), any());
        verify(riskMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    void onMessage_riskyUrl_disablesAndNotifies() {
        ShortLinkRiskEvent event = ShortLinkRiskEvent.builder()
                .eventId("evt-2")
                .fullShortUrl("test.cn/risky")
                .originUrl("https://fake-paypal-security-verify.com/login")
                .gid("g1")
                .userId(100L)
                .build();

        ShortLinkDO linkDO = new ShortLinkDO();
        linkDO.setEnableStatus(LinkEnableStatusEnum.ENABLE.getCode());
        when(shortLinkMapper.selectOne(any())).thenReturn(linkDO);

        when(riskControlService.checkUrlRisk(anyString()))
                .thenReturn(ShortLinkRiskCheckRespDTO.builder()
                        .safe(false).riskType("PHISHING").summary("钓鱼").detail("疑似钓鱼").build());
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        consumer.onMessage(event);

        verify(shortLinkMapper).update(isNull(), any());
        verify(rocketMQTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
        verify(riskMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    void onMessage_alreadyBanned_skipsAiCheck() {
        ShortLinkRiskEvent event = ShortLinkRiskEvent.builder()
                .eventId("evt-3")
                .fullShortUrl("test.cn/banned")
                .originUrl("https://banned.com")
                .gid("g1")
                .userId(100L)
                .build();

        ShortLinkDO linkDO = new ShortLinkDO();
        linkDO.setEnableStatus(LinkEnableStatusEnum.BANNED.getCode());
        when(shortLinkMapper.selectOne(any())).thenReturn(linkDO);

        consumer.onMessage(event);

        verify(riskControlService, never()).checkUrlRisk(anyString());
        verify(riskMetrics).recordConsumeSuccess(any(Duration.class));
    }

    @Test
    void onMessage_runtimeException_recordsFailure() {
        ShortLinkRiskEvent event = ShortLinkRiskEvent.builder()
                .eventId("evt-4")
                .fullShortUrl("test.cn/error")
                .originUrl("https://error.com")
                .gid("g1")
                .userId(100L)
                .build();

        when(shortLinkMapper.selectOne(any())).thenThrow(new RuntimeException("DB error"));

        try {
            consumer.onMessage(event);
        } catch (RuntimeException ignored) {
        }

        verify(riskMetrics).recordConsumeFailure(any(Duration.class));
    }
}
