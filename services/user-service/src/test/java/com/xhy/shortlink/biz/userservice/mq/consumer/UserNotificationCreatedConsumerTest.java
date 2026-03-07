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

import com.xhy.shortlink.biz.userservice.mq.event.UserNotificationCreatedEvent;
import com.xhy.shortlink.biz.userservice.websocket.NotificationSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserNotificationCreatedConsumerTest {

    @Mock
    private NotificationSessionManager notificationSessionManager;

    @InjectMocks
    private UserNotificationCreatedConsumer consumer;

    @Test
    void onMessage_pushesNotificationToCurrentUserSessions() {
        UserNotificationCreatedEvent event = UserNotificationCreatedEvent.builder()
                .notificationId(1L)
                .userId(100L)
                .type(1)
                .title("短链接封禁提醒")
                .content("test")
                .readFlag(0)
                .createTime(new Date())
                .build();

        consumer.onMessage(event);

        ArgumentCaptor<com.xhy.shortlink.biz.userservice.dto.resp.UserNotificationRespDTO> captor = ArgumentCaptor.forClass(com.xhy.shortlink.biz.userservice.dto.resp.UserNotificationRespDTO.class);
        verify(notificationSessionManager).pushNotification(eq(100L), captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("短链接封禁提醒", captor.getValue().getTitle());
    }
}
