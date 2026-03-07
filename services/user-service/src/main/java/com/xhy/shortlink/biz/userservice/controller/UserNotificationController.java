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

package com.xhy.shortlink.biz.userservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.biz.userservice.dto.req.NotificationPageReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.NotificationReadReqDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserNotificationRespDTO;
import com.xhy.shortlink.biz.userservice.service.UserNotificationService;
import com.xhy.shortlink.framework.starter.convention.result.Result;
import com.xhy.shortlink.framework.starter.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserNotificationController {

    private final UserNotificationService userNotificationService;

    @GetMapping("/api/short-link/admin/v1/notification")
    public Result<IPage<UserNotificationRespDTO>> pageNotification(NotificationPageReqDTO requestParam) {
        return Results.success(userNotificationService.pageNotification(requestParam));
    }

    @GetMapping("/api/short-link/admin/v1/notification/unread-count")
    public Result<Integer> queryUnreadCount() {
        return Results.success(userNotificationService.queryUnreadCount());
    }

    @PutMapping("/api/short-link/admin/v1/notification/read")
    public Result<Void> markRead(@RequestBody @Valid NotificationReadReqDTO requestParam) {
        userNotificationService.markRead(requestParam);
        return Results.success();
    }

    @PutMapping("/api/short-link/admin/v1/notification/read-all")
    public Result<Void> markAllRead() {
        userNotificationService.markAllRead();
        return Results.success();
    }
}
