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

package com.xhy.shortlink.project.mq.producer.RocketMQ;


import com.xhy.shortlink.project.mq.base.BaseSendExtendDTO;
import com.xhy.shortlink.project.mq.event.ShortLinkStatsRecordEvent;
import com.xhy.shortlink.project.mq.producer.AbstractCommonSendProduceTemplate;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.project.common.constant.RocketMQConstant.STATIC_TOPIC;

/**
 * 短链接监控状态保存消息队列生产者 RocketMQ实现方式
 */
@Component
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "RocketMQ")
public class ShortLinkStaticSaveRocketMQProducer extends AbstractCommonSendProduceTemplate<ShortLinkStatsRecordEvent> implements ShortLinkMessageProducer<ShortLinkStatsRecordEvent> {

    // 构造注入 RocketMQTemplate
    public ShortLinkStaticSaveRocketMQProducer(@Autowired RocketMQTemplate rocketMQTemplate) {
        super(rocketMQTemplate);
    }

    // 接口业务
    @Override
    public void send(ShortLinkStatsRecordEvent event) {
        // 调用父类的通用逻辑
        super.sendMessage(event);
    }

    // 2. 实现父类抽象方法：构建 RocketMQ 特有的参数
    @Override
    protected BaseSendExtendDTO buildBaseSendExtendDTO(ShortLinkStatsRecordEvent messageEvent) {
        return BaseSendExtendDTO.builder()
                .eventName("短链接统计消息")
                .topic(STATIC_TOPIC)
                .sendType(BaseSendExtendDTO.SendType.SYNC) // 同步发送消息
                .keys(messageEvent.getEventId())
                .sentTimeout(2000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(ShortLinkStatsRecordEvent messageEvent, BaseSendExtendDTO baseSendExtendDTO) {
        // 构建 Spring Message
        return MessageBuilder.withPayload(messageEvent)
                .setHeader(MessageConst.PROPERTY_KEYS, baseSendExtendDTO.getKeys())
                .setHeader(MessageConst.PROPERTY_TAGS, baseSendExtendDTO.getTag())
                .build();
    }
}
