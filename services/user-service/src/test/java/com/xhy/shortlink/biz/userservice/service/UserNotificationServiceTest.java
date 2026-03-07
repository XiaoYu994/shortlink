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

package com.xhy.shortlink.biz.userservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.biz.userservice.dto.req.NotificationPageReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.NotificationReadReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserNotificationRespDTO;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import com.xhy.shortlink.framework.starter.user.core.UserInfoDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class UserNotificationServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserNotificationService userNotificationService;

    @MockBean
    private com.xhy.shortlink.biz.userservice.websocket.NotificationSessionManager notificationSessionManager;

    private Long currentUserId;
    private String currentUsername;

    @BeforeEach
    void setUp() {
        currentUsername = "notify_" + System.currentTimeMillis();
        UserRegisterReqDTO registerReq = new UserRegisterReqDTO();
        registerReq.setUsername(currentUsername);
        registerReq.setPassword("Test123456");
        registerReq.setRealName("通知测试用户");
        registerReq.setPhone("13800138000");
        registerReq.setMail(currentUsername + "@example.com");
        userService.register(registerReq);
        currentUserId = userService.getUserByUsername(currentUsername).getId();
        UserContext.setUser(UserInfoDTO.builder()
                .userId(String.valueOf(currentUserId))
                .username(currentUsername)
                .realName("通知测试用户")
                .token("test-token")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void pageNotification_returnsCurrentUserRecords() {
        NotificationPageReqDTO reqDTO = new NotificationPageReqDTO();
        reqDTO.setCurrent(1L);
        reqDTO.setSize(10L);

        IPage<UserNotificationRespDTO> result = userNotificationService.pageNotification(reqDTO);

        assertNotNull(result);
        assertNotNull(result.getRecords());
    }

    @Test
    void unreadCount_defaultsToZero() {
        Integer unreadCount = userNotificationService.queryUnreadCount();
        assertNotNull(unreadCount);
        assertEquals(0, unreadCount);
    }

    @Test
    void readAndReadAll_doNotThrowForCurrentUser() {
        NotificationReadReqDTO reqDTO = new NotificationReadReqDTO();
        reqDTO.setId(1L);
        assertDoesNotThrow(() -> userNotificationService.markRead(reqDTO));
        assertDoesNotThrow(() -> userNotificationService.markAllRead());
    }
}
