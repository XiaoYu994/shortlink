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

package com.xhy.shortlink.biz.userservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhy.shortlink.biz.userservice.dto.resp.UserNotificationRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSessionManagerTest {

    @Test
    void pushNotification_sendsMessageToRegisteredSession() throws Exception {
        NotificationSessionManager sessionManager = new NotificationSessionManager(new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        sessionManager.register(100L, session);

        UserNotificationRespDTO respDTO = new UserNotificationRespDTO();
        respDTO.setId(1L);
        respDTO.setTitle("短链接封禁提醒");

        sessionManager.pushNotification(100L, respDTO);

        verify(session).sendMessage(any(TextMessage.class));
    }
}
