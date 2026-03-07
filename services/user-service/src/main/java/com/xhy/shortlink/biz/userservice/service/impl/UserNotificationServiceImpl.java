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

package com.xhy.shortlink.biz.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.biz.userservice.dao.entity.UserNotificationDO;
import com.xhy.shortlink.biz.userservice.dao.mapper.UserNotificationMapper;
import com.xhy.shortlink.biz.userservice.dto.req.NotificationPageReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.NotificationReadReqDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserNotificationRespDTO;
import com.xhy.shortlink.biz.userservice.service.UserNotificationService;
import com.xhy.shortlink.framework.starter.common.toolkit.BeanUtil;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNotificationServiceImpl implements UserNotificationService {

    private final UserNotificationMapper userNotificationMapper;

    @Override
    public IPage<UserNotificationRespDTO> pageNotification(NotificationPageReqDTO requestParam) {
        Page<UserNotificationDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        LambdaQueryWrapper<UserNotificationDO> queryWrapper = new LambdaQueryWrapper<UserNotificationDO>()
                .eq(UserNotificationDO::getUserId, Long.parseLong(UserContext.getUserId()))
                .orderByDesc(UserNotificationDO::getCreateTime);
        if (requestParam.getReadFlag() != null) {
            queryWrapper.eq(UserNotificationDO::getReadFlag, requestParam.getReadFlag());
        }
        IPage<UserNotificationDO> resultPage = userNotificationMapper.selectPage(page, queryWrapper);
        Page<UserNotificationRespDTO> respPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<UserNotificationRespDTO> records = BeanUtil.convert(resultPage.getRecords(), UserNotificationRespDTO.class);
        respPage.setRecords(records);
        return respPage;
    }

    @Override
    public Integer queryUnreadCount() {
        Long count = userNotificationMapper.selectCount(new LambdaQueryWrapper<UserNotificationDO>()
                .eq(UserNotificationDO::getUserId, Long.parseLong(UserContext.getUserId()))
                .eq(UserNotificationDO::getReadFlag, 0));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public void markRead(NotificationReadReqDTO requestParam) {
        userNotificationMapper.update(null, new LambdaUpdateWrapper<UserNotificationDO>()
                .eq(UserNotificationDO::getId, requestParam.getId())
                .eq(UserNotificationDO::getUserId, Long.parseLong(UserContext.getUserId()))
                .set(UserNotificationDO::getReadFlag, 1));
    }

    @Override
    public void markAllRead() {
        userNotificationMapper.update(null, new LambdaUpdateWrapper<UserNotificationDO>()
                .eq(UserNotificationDO::getUserId, Long.parseLong(UserContext.getUserId()))
                .eq(UserNotificationDO::getReadFlag, 0)
                .set(UserNotificationDO::getReadFlag, 1));
    }
}
