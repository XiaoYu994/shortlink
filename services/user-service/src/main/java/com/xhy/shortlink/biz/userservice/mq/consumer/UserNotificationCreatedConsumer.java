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

package com.xhy.shortlink.biz.userservice.mq.consumer;

import com.xhy.shortlink.biz.userservice.dto.resp.UserNotificationRespDTO;
import com.xhy.shortlink.biz.userservice.mq.event.UserNotificationCreatedEvent;
import com.xhy.shortlink.biz.userservice.websocket.NotificationSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.biz.userservice.common.constant.RocketMQConstant.USER_NOTIFICATION_CREATED_GROUP;
import static com.xhy.shortlink.biz.userservice.common.constant.RocketMQConstant.USER_NOTIFICATION_CREATED_TOPIC;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = USER_NOTIFICATION_CREATED_TOPIC, consumerGroup = USER_NOTIFICATION_CREATED_GROUP)
public class UserNotificationCreatedConsumer implements RocketMQListener<UserNotificationCreatedEvent> {

    private final NotificationSessionManager notificationSessionManager;

    @Override
    public void onMessage(UserNotificationCreatedEvent event) {
        notificationSessionManager.pushNotification(event.getUserId(), convert(event));
    }

    private UserNotificationRespDTO convert(UserNotificationCreatedEvent event) {
        UserNotificationRespDTO respDTO = new UserNotificationRespDTO();
        respDTO.setId(event.getNotificationId());
        respDTO.setType(event.getType());
        respDTO.setTitle(event.getTitle());
        respDTO.setContent(event.getContent());
        respDTO.setReadFlag(event.getReadFlag());
        respDTO.setCreateTime(event.getCreateTime());
        return respDTO;
    }
}
