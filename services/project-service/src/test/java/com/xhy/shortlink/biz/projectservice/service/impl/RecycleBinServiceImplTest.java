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

package com.xhy.shortlink.biz.projectservice.service.impl;

import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.biz.projectservice.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkCoreService;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecycleBinServiceImplTest {

    @InjectMocks
    private RecycleBinServiceImpl recycleBinService;

    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private ShortLinkColdMapper shortLinkColdMapper;
    @Mock
    private ShortLinkCoreService shortLinkCoreService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void recycleBinSave_success() {
        ShortLinkRecycleBinSaveReqDTO req = new ShortLinkRecycleBinSaveReqDTO();
        req.setGid("g1");
        req.setFullShortUrl("test.cn/abc");

        when(shortLinkMapper.update(any(ShortLinkDO.class), any())).thenReturn(1);
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        assertDoesNotThrow(() -> recycleBinService.recycleBinSave(req));

        verify(shortLinkMapper).update(argThat(entity ->
                entity.getEnableStatus().equals(LinkEnableStatusEnum.NOT_ENABLED.getCode())), any());
        verify(stringRedisTemplate).delete(contains("test.cn/abc"));
    }

    @Test
    void recoverShortlink_success() {
        ShortLinkRecycleBinRecoverReqDTO req = new ShortLinkRecycleBinRecoverReqDTO();
        req.setGid("g1");
        req.setFullShortUrl("test.cn/abc");
        req.setEnableStatus(LinkEnableStatusEnum.NOT_ENABLED.getCode());

        when(shortLinkMapper.update(any(ShortLinkDO.class), any())).thenReturn(1);
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        assertDoesNotThrow(() -> recycleBinService.recoverShortlink(req));

        verify(shortLinkMapper).update(argThat(entity ->
                entity.getEnableStatus().equals(LinkEnableStatusEnum.ENABLE.getCode())), any());
    }

    @Test
    void recoverShortlink_banned_throwsClientException() {
        ShortLinkRecycleBinRecoverReqDTO req = new ShortLinkRecycleBinRecoverReqDTO();
        req.setGid("g1");
        req.setFullShortUrl("test.cn/abc");
        req.setEnableStatus(LinkEnableStatusEnum.BANNED.getCode());

        assertThrows(ClientException.class, () -> recycleBinService.recoverShortlink(req));
        verify(shortLinkMapper, never()).update(any(), any());
    }

    @Test
    void removeShortlink_success() {
        ShortLinkRecycleBinRemoveReqDTO req = new ShortLinkRecycleBinRemoveReqDTO();
        req.setGid("g1");
        req.setFullShortUrl("test.cn/abc");

        when(shortLinkMapper.delete(any())).thenReturn(1);

        assertDoesNotThrow(() -> recycleBinService.removeShortlink(req));

        verify(shortLinkMapper).delete(any());
    }
}
