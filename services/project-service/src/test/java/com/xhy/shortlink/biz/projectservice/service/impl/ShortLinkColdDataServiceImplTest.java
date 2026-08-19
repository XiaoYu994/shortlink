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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.projectservice.config.ColdDataProperties;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.metrics.ShortLinkMetrics;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkCacheProducer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkColdDataServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ShortLinkDO.class);
        TableInfoHelper.initTableInfo(assistant, ShortLinkColdDO.class);
        TableInfoHelper.initTableInfo(assistant, ShortLinkGoToDO.class);
        TableInfoHelper.initTableInfo(assistant, ShortLinkGoToColdDO.class);
    }

    @InjectMocks
    private ShortLinkColdDataServiceImpl coldDataService;

    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private ShortLinkGoToMapper shortLinkGoToMapper;
    @Mock
    private ShortLinkColdMapper shortLinkColdMapper;
    @Mock
    private ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    @Mock
    private ShortLinkCacheProducer cacheProducer;
    @Mock
    private ColdDataProperties coldDataProperties;
    @Mock
    private ShortLinkMetrics shortLinkMetrics;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void mergeSorted_byCreateTime_keepsDescendingOrder() {
        Date newer = new Date(2_000L);
        Date older = new Date(1_000L);
        ShortLinkDO hot = ShortLinkDO.builder()
                .fullShortUrl("hot.cn/a")
                .domain("hot.cn")
                .build();
        hot.setCreateTime(newer);
        ShortLinkColdDO cold = ShortLinkColdDO.builder()
                .fullShortUrl("cold.cn/b")
                .domain("cold.cn")
                .build();
        cold.setCreateTime(older);

        List<ShortLinkPageRespDTO> merged = coldDataService.mergeSorted(
                List.of(hot), List.of(cold), null, null);

        assertEquals(2, merged.size());
        assertEquals("hot.cn/a", merged.get(0).getFullShortUrl());
        assertEquals("cold.cn/b", merged.get(1).getFullShortUrl());
    }

    @Test
    void mergeSorted_byTotalPv_interleavesHotAndCold() {
        ShortLinkDO hotLow = ShortLinkDO.builder()
                .fullShortUrl("hot.cn/low")
                .domain("hot.cn")
                .totalPv(10L)
                .build();
        hotLow.setCreateTime(new Date(1_000L));
        ShortLinkDO hotHigh = ShortLinkDO.builder()
                .fullShortUrl("hot.cn/high")
                .domain("hot.cn")
                .totalPv(30L)
                .build();
        hotHigh.setCreateTime(new Date(2_000L));
        ShortLinkColdDO coldMid = ShortLinkColdDO.builder()
                .fullShortUrl("cold.cn/mid")
                .domain("cold.cn")
                .totalPv(20L)
                .build();
        coldMid.setCreateTime(new Date(1_500L));

        List<ShortLinkPageRespDTO> merged = coldDataService.mergeSorted(
                List.of(hotHigh, hotLow), List.of(coldMid), "totalPv", null);

        assertEquals(List.of("hot.cn/high", "cold.cn/mid", "hot.cn/low"),
                merged.stream().map(ShortLinkPageRespDTO::getFullShortUrl).toList());
    }

    @Test
    void mergeSorted_reordersDbNullsFirstInput() {
        ShortLinkDO hotNull = ShortLinkDO.builder()
                .fullShortUrl("hot.cn/null")
                .domain("hot.cn")
                .build();
        hotNull.setCreateTime(new Date(3_000L));
        ShortLinkDO hotHigh = ShortLinkDO.builder()
                .fullShortUrl("hot.cn/high")
                .domain("hot.cn")
                .totalPv(100L)
                .build();
        hotHigh.setCreateTime(new Date(1_000L));
        ShortLinkColdDO coldMid = ShortLinkColdDO.builder()
                .fullShortUrl("cold.cn/mid")
                .domain("cold.cn")
                .totalPv(50L)
                .build();
        coldMid.setCreateTime(new Date(2_000L));

        List<ShortLinkPageRespDTO> merged = coldDataService.mergeSorted(
                List.of(hotNull, hotHigh), List.of(coldMid), "totalPv", null);

        assertEquals(List.of("hot.cn/high", "cold.cn/mid", "hot.cn/null"),
                merged.stream().map(ShortLinkPageRespDTO::getFullShortUrl).toList());
    }

    @Test
    void migrateInactiveLinks_disabled_doesNothing() {
        when(coldDataProperties.getEnabled()).thenReturn(false);

        assertEquals(0, coldDataService.migrateInactiveLinks());
        verify(shortLinkMapper, never()).selectPage(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrateInactiveLinks_noRecords_exits() {
        when(coldDataProperties.getEnabled()).thenReturn(true);
        when(coldDataProperties.getDays()).thenReturn(90);
        when(coldDataProperties.getBatchSize()).thenReturn(200);

        Page<ShortLinkDO> emptyPage = new Page<>();
        emptyPage.setRecords(Collections.emptyList());
        when(shortLinkMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage);

        assertEquals(0, coldDataService.migrateInactiveLinks());
        verify(shortLinkColdMapper, never()).insert(any(ShortLinkColdDO.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrateInactiveLinks_withRecords_migratesSuccessfully() {
        when(coldDataProperties.getEnabled()).thenReturn(true);
        when(coldDataProperties.getDays()).thenReturn(90);
        when(coldDataProperties.getBatchSize()).thenReturn(200);

        ShortLinkDO record = ShortLinkDO.builder()
                .id(123L)
                .gid("g1")
                .fullShortUrl("test.cn/old")
                .originUrl("https://example.com")
                .build();

        Page<ShortLinkDO> firstPage = new Page<>();
        firstPage.setRecords(List.of(record));
        Page<ShortLinkDO> emptyPage = new Page<>();
        emptyPage.setRecords(Collections.emptyList());
        when(shortLinkMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(firstPage)
                .thenReturn(emptyPage);

        when(shortLinkColdMapper.selectCount(any())).thenReturn(0L);

        ShortLinkGoToDO goTo = ShortLinkGoToDO.builder()
                .id(456L)
                .gid("g1").fullShortUrl("test.cn/old").build();
        when(shortLinkGoToMapper.selectOne(any())).thenReturn(goTo);
        when(shortLinkGoToColdMapper.selectCount(any())).thenReturn(0L);

        assertEquals(1, coldDataService.migrateInactiveLinks());

        verify(shortLinkColdMapper).insert(org.mockito.ArgumentMatchers.<ShortLinkColdDO>argThat(cold -> cold.getId() == null));
        verify(shortLinkGoToColdMapper).insert(org.mockito.ArgumentMatchers.<ShortLinkGoToColdDO>argThat(route -> route.getId() == null));
        verify(cacheProducer).sendMessage("test.cn/old");
        verify(shortLinkMetrics).recordColdMigrationBatch(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrateInactiveLinks_allFailed_stopsWithoutLoop() {
        when(coldDataProperties.getEnabled()).thenReturn(true);
        when(coldDataProperties.getDays()).thenReturn(90);
        when(coldDataProperties.getBatchSize()).thenReturn(200);

        ShortLinkDO record = ShortLinkDO.builder()
                .gid("g1")
                .fullShortUrl("test.cn/fail")
                .originUrl("https://example.com")
                .build();
        Page<ShortLinkDO> firstPage = new Page<>();
        firstPage.setRecords(List.of(record));
        when(shortLinkMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(firstPage);
        assertEquals(0, coldDataService.migrateInactiveLinks());
        verify(shortLinkMapper).selectPage(any(IPage.class), any(LambdaQueryWrapper.class));
        verify(shortLinkMetrics).recordColdMigrationFailure();
    }
}
