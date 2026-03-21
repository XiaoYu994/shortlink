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

package com.xhy.shortlink.biz.statsservice.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.biz.api.stats.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.xhy.shortlink.biz.statsservice.dao.entity.GroupDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessLogsDO;
import com.xhy.shortlink.biz.statsservice.dao.mapper.*;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import com.xhy.shortlink.framework.starter.user.core.UserInfoDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkStatsServiceImplTest {

    @InjectMocks
    private ShortLinkStatsServiceImpl shortLinkStatsService;

    @Mock
    private LinkAccessStatsMapper linkAccessStatsMapper;
    @Mock
    private LinkLocaleStatsMapper linkLocaleStatsMapper;
    @Mock
    private LinkAccessLogsMapper linkAccessLogsMapper;
    @Mock
    private LinkBrowserStatsMapper linkBrowserStatsMapper;
    @Mock
    private LinkOsStatsMapper linkOsStatsMapper;
    @Mock
    private LinkDeviceStatsMapper linkDeviceStatsMapper;
    @Mock
    private LinkNetworkStatsMapper linkNetworkStatsMapper;
    @Mock
    private LinkGroupMapper groupMapper;

    @BeforeEach
    void setUp() {
        UserContext.setUser(UserInfoDTO.builder().username("testuser").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void shortLinkStatsAccessRecord_buildsDedicatedPageForBaseMapper() {
        ShortLinkStatsAccessRecordReqDTO requestParam = new ShortLinkStatsAccessRecordReqDTO();
        requestParam.setCurrent(2);
        requestParam.setSize(10);
        requestParam.setGid("g1");
        requestParam.setFullShortUrl("test.cn/abc");
        requestParam.setStartDate("2026-03-15 00:00:00");
        requestParam.setEndDate("2026-03-21 23:59:59");
        requestParam.setEnableStatus(0);

        when(groupMapper.selectList(any())).thenReturn(List.of(GroupDO.builder()
                .gid("g1")
                .username("testuser")
                .build()));

        Page<LinkAccessLogsDO> logPage = new Page<>(2, 10);
        logPage.setRecords(List.of(LinkAccessLogsDO.builder()
                .user("uv-1")
                .ip("127.0.0.1")
                .fullShortUrl("test.cn/abc")
                .build()));
        when(linkAccessLogsMapper.selectPage(any(IPage.class), any())).thenReturn(logPage);
        when(linkAccessLogsMapper.selectUvTypeByUser(any())).thenReturn(Collections.emptyList());

        IPage<?> result = shortLinkStatsService.shortLinkStatsAccessRecord(requestParam);

        assertEquals(1, result.getRecords().size());

        ArgumentCaptor<IPage> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(linkAccessLogsMapper).selectPage(pageCaptor.capture(), any());
        IPage capturedPage = pageCaptor.getValue();
        assertNotSame(requestParam, capturedPage);
        assertEquals(2L, capturedPage.getCurrent());
        assertEquals(10L, capturedPage.getSize());
    }
}
