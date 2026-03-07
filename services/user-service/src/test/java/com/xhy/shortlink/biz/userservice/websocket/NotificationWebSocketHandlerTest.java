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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketHandlerTest {

    @Mock
    private NotificationTokenAuthService notificationTokenAuthService;
    @Mock
    private NotificationSessionManager notificationSessionManager;
    @Mock
    private WebSocketSession session;

    @Test
    void afterConnectionEstablished_registersSessionWhenTokenValid() throws Exception {
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(notificationTokenAuthService, notificationSessionManager);
        when(session.getUri()).thenReturn(URI.create("ws://127.0.0.1/api/short-link/admin/v1/notification/ws?token=abc"));
        when(notificationTokenAuthService.resolveUserId("abc")).thenReturn(100L);

        handler.afterConnectionEstablished(session);

        verify(notificationSessionManager).register(100L, session);
    }

    @Test
    void afterConnectionEstablished_closesSessionWhenTokenInvalid() throws Exception {
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(notificationTokenAuthService, notificationSessionManager);
        when(session.getUri()).thenReturn(URI.create("ws://127.0.0.1/api/short-link/admin/v1/notification/ws?token=bad"));
        when(notificationTokenAuthService.resolveUserId("bad")).thenReturn(null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }
}
