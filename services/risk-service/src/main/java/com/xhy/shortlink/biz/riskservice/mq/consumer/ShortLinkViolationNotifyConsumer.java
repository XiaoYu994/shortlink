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

import com.xhy.shortlink.biz.riskservice.dao.entity.UserNotificationDO;
import com.xhy.shortlink.biz.riskservice.dao.mapper.UserNotificationMapper;
import com.xhy.shortlink.biz.riskservice.metrics.RiskMetrics;
import com.xhy.shortlink.biz.riskservice.mq.event.ShortLinkViolationEvent;
import com.xhy.shortlink.framework.starter.idempotent.annotation.Idempotent;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentSceneEnum;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.time.Duration;

import static com.xhy.shortlink.biz.riskservice.common.constant.RocketMQConstant.NOTIFY_GROUP;
import static com.xhy.shortlink.biz.riskservice.common.constant.RocketMQConstant.NOTIFY_TOPIC;

/**
 * 违规通知消费者
 *
 * @author XiaoYu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = NOTIFY_TOPIC, consumerGroup = NOTIFY_GROUP)
public class ShortLinkViolationNotifyConsumer implements RocketMQListener<ShortLinkViolationEvent> {

    private final UserNotificationMapper userNotificationMapper;
    private final RiskMetrics riskMetrics;

    @Override
    @Idempotent(
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            key = "#event.eventId",
            uniqueKeyPrefix = "violation-notify:",
            keyTimeout = 7200
    )
    public void onMessage(ShortLinkViolationEvent event) {
        long startNanos = System.nanoTime();
        try {
            log.info("收到违规通知任务: {}", event.getFullShortUrl());
            try {
                userNotificationMapper.insert(UserNotificationDO.builder()
                        .userId(event.getUserId())
                        .type(1)
                        .title("短链接封禁提醒")
                        .eventId(event.getEventId())
                        .content(String.format(
                                "您的短链接 %s 因 [%s] 被系统检测为违规，现已封禁。",
                                event.getFullShortUrl(), event.getReason()))
                        .readFlag(0)
                        .createTime(new Date())
                        .build());
                log.info("已生成站内信通知");
            } catch (DuplicateKeyException e) {
                log.warn("数据库已存在该通知，忽略。eventId: {}", event.getEventId());
            }
            riskMetrics.recordConsumeSuccess(Duration.ofNanos(System.nanoTime() - startNanos));
        } catch (RuntimeException ex) {
            riskMetrics.recordConsumeFailure(Duration.ofNanos(System.nanoTime() - startNanos));
            throw ex;
        }
    }
}
