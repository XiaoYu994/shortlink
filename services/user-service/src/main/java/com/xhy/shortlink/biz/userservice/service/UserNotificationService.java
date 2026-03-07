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
import com.xhy.shortlink.biz.userservice.dto.resp.UserNotificationRespDTO;

public interface UserNotificationService {

    IPage<UserNotificationRespDTO> pageNotification(NotificationPageReqDTO requestParam);

    Integer queryUnreadCount();

    void markRead(NotificationReadReqDTO requestParam);

    void markAllRead();
}
