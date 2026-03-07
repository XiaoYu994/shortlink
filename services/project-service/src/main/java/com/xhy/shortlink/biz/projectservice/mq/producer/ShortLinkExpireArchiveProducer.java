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

package com.xhy.shortlink.biz.projectservice.mq.producer;

import com.xhy.shortlink.biz.projectservice.mq.base.BaseSendExtendDTO;
import com.xhy.shortlink.biz.projectservice.mq.event.ShortLinkExpireArchiveEvent;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.biz.projectservice.common.constant.RocketMQConstant.EXPIRE_ARCHIVE_TOPIC;

/**
 * 过期短链接归档消息生产者
 *
 * @author XiaoYu
 */
@Component
public class ShortLinkExpireArchiveProducer extends AbstractCommonSendProduceTemplate<ShortLinkExpireArchiveEvent> {

    public ShortLinkExpireArchiveProducer(@Autowired RocketMQTemplate rocketMQTemplate) {
        super(rocketMQTemplate);
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendDTO(ShortLinkExpireArchiveEvent messageEvent) {
        long delayTime = 0L;
        if (messageEvent.getExpireAt() != null) {
            delayTime = Math.max(0L, messageEvent.getExpireAt().getTime() - System.currentTimeMillis());
        }
        return BaseSendExtendDTO.builder()
                .eventName("过期短链归档")
                .topic(EXPIRE_ARCHIVE_TOPIC)
                .keys(messageEvent.getEventId())
                .delayTime(delayTime)
                .sendType(BaseSendExtendDTO.SendType.SYNC)
                .build();
    }

    @Override
    protected Message<?> buildMessage(ShortLinkExpireArchiveEvent messageEvent, BaseSendExtendDTO baseSendExtendDTO) {
        return MessageBuilder.withPayload(messageEvent)
                .setHeader(MessageConst.PROPERTY_KEYS, messageEvent.getFullShortUrl())
                .build();
    }
}
