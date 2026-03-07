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

import com.xhy.shortlink.biz.riskservice.dao.mapper.UserNotificationMapper;
import com.xhy.shortlink.biz.riskservice.metrics.RiskMetrics;
import com.xhy.shortlink.biz.riskservice.mq.event.ShortLinkViolationEvent;
import com.xhy.shortlink.biz.riskservice.mq.producer.UserNotificationCreatedProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShortLinkViolationNotifyConsumerTest {

    @Mock
    private UserNotificationMapper userNotificationMapper;
    @Mock
    private UserNotificationCreatedProducer userNotificationCreatedProducer;
    @Mock
    private RiskMetrics riskMetrics;

    @InjectMocks
    private ShortLinkViolationNotifyConsumer consumer;

    @Test
    void onMessage_persistsNotificationAndPublishesCreatedEvent() {
        ShortLinkViolationEvent event = ShortLinkViolationEvent.builder()
                .eventId("evt-notify-1")
                .userId(100L)
                .fullShortUrl("test.cn/banned")
                .reason("PHISHING")
                .build();

        consumer.onMessage(event);

        verify(userNotificationMapper).insert(any(com.xhy.shortlink.biz.riskservice.dao.entity.UserNotificationDO.class));
        verify(userNotificationCreatedProducer).sendMessage(any());
        verify(riskMetrics).recordConsumeSuccess(any(Duration.class));
    }
}
