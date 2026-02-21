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
import com.xhy.shortlink.biz.projectservice.mq.event.ShortLinkStatsRecordEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.biz.projectservice.common.constant.RocketMQConstant.STATS_RECORD_TOPIC;

/**
 * 短链接统计数据记录消息生产者
 *
 * @author XiaoYu
 */
@Slf4j
@Component
public class ShortLinkStatsProducer extends AbstractCommonSendProduceTemplate<ShortLinkStatsRecordEvent> {

    public ShortLinkStatsProducer(@Autowired RocketMQTemplate rocketMQTemplate) {
        super(rocketMQTemplate);
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendDTO(ShortLinkStatsRecordEvent messageEvent) {
        return BaseSendExtendDTO.builder()
                .eventName("统计数据记录")
                .topic(STATS_RECORD_TOPIC)
                .keys(messageEvent.getFullShortUrl())
                .sendType(BaseSendExtendDTO.SendType.ASYNC)
                .sentTimeout(2000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(ShortLinkStatsRecordEvent messageEvent, BaseSendExtendDTO baseSendExtendDTO) {
        return MessageBuilder.withPayload(messageEvent)
                .setHeader(MessageConst.PROPERTY_KEYS, messageEvent.getFullShortUrl())
                .build();
    }
}
