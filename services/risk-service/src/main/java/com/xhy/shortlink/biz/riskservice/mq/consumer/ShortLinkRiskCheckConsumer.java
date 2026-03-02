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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.biz.riskservice.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.biz.riskservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.riskservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.riskservice.dto.resp.ShortLinkRiskCheckRespDTO;
import com.xhy.shortlink.biz.riskservice.metrics.RiskMetrics;
import com.xhy.shortlink.biz.riskservice.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.biz.riskservice.mq.event.ShortLinkViolationEvent;
import com.xhy.shortlink.biz.riskservice.service.UrlRiskControlService;
import com.xhy.shortlink.framework.starter.idempotent.annotation.Idempotent;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentSceneEnum;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;

import static com.xhy.shortlink.biz.riskservice.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;
import static com.xhy.shortlink.biz.riskservice.common.constant.RocketMQConstant.*;

/**
 * 短链接风控检测消费者
 *
 * @author XiaoYu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RISK_CHECK_TOPIC, consumerGroup = RISK_CHECK_GROUP)
public class ShortLinkRiskCheckConsumer implements RocketMQListener<ShortLinkRiskEvent> {

    private final UrlRiskControlService riskControlService;
    private final ShortLinkMapper shortLinkMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final RiskMetrics riskMetrics;

    @Override
    @Idempotent(
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            key = "#event.eventId",
            uniqueKeyPrefix = "risk-check:",
            keyTimeout = 7200
    )
    public void onMessage(ShortLinkRiskEvent event) {
        long startNanos = System.nanoTime();
        try {
            log.info("开始对短链接进行 AI 风控审核: {}", event.getFullShortUrl());

            // 已封禁则跳过，节省 AI Token
            ShortLinkDO linkDO = shortLinkMapper.selectOne(
                    Wrappers.lambdaQuery(ShortLinkDO.class)
                            .eq(ShortLinkDO::getGid, event.getGid())
                            .eq(ShortLinkDO::getFullShortUrl, event.getFullShortUrl())
                            .select(ShortLinkDO::getEnableStatus));
            if (linkDO != null && linkDO.getEnableStatus() == LinkEnableStatusEnum.BANNED.getCode()) {
                log.info("该链接已被封禁，跳过 AI 检测: {}", event.getFullShortUrl());
                riskMetrics.recordConsumeSuccess(Duration.ofNanos(System.nanoTime() - startNanos));
                return;
            }

            ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(event.getOriginUrl());
            if (!result.isSafe()) {
                log.warn("发现违规链接！URL: {}, 类型: {}", event.getFullShortUrl(), result.getRiskType());
                disableLink(event);
                sendViolationNotification(event, result.getSummary());
            } else {
                log.info("AI 审核通过: {}", event.getFullShortUrl());
            }
            riskMetrics.recordConsumeSuccess(Duration.ofNanos(System.nanoTime() - startNanos));
        } catch (RuntimeException ex) {
            riskMetrics.recordConsumeFailure(Duration.ofNanos(System.nanoTime() - startNanos));
            throw ex;
        }
    }

    private void disableLink(ShortLinkRiskEvent event) {
        shortLinkMapper.update(null, Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, event.getGid())
                .eq(ShortLinkDO::getFullShortUrl, event.getFullShortUrl())
                .set(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.BANNED.getCode()));

        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, event.getFullShortUrl()));

        try {
            rocketMQTemplate.convertAndSend(CACHE_INVALIDATE_TOPIC, event.getFullShortUrl());
        } catch (Exception e) {
            log.error("风控封禁广播发送失败", e);
        }
    }

    private void sendViolationNotification(ShortLinkRiskEvent riskEvent, String reason) {
        try {
            ShortLinkViolationEvent event = ShortLinkViolationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(riskEvent.getUserId())
                    .fullShortUrl(riskEvent.getFullShortUrl())
                    .gid(riskEvent.getGid())
                    .reason(reason)
                    .time(LocalDateTime.now())
                    .build();
            rocketMQTemplate.convertAndSend(NOTIFY_TOPIC, event);
        } catch (Exception e) {
            log.error("发送违规通知失败", e);
        }
    }
}
