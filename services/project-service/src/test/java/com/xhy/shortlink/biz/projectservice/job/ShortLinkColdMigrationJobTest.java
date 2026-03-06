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

package com.xhy.shortlink.biz.projectservice.job;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortLinkColdMigrationJobTest {

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
    private ShortLinkColdMigrationJob migrationJob;

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

    @Test
    void migrateColdLinks_disabled_doesNothing() {
        when(coldDataProperties.getEnabled()).thenReturn(false);

        migrationJob.migrateColdLinks();

        verify(shortLinkMapper, never()).selectPage(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrateColdLinks_noRecords_exits() {
        when(coldDataProperties.getEnabled()).thenReturn(true);
        when(coldDataProperties.getDays()).thenReturn(90);
        when(coldDataProperties.getBatchSize()).thenReturn(200);

        Page<ShortLinkDO> emptyPage = new Page<>();
        emptyPage.setRecords(Collections.emptyList());
        when(shortLinkMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage);

        migrationJob.migrateColdLinks();

        verify(shortLinkColdMapper, never()).insert(any(ShortLinkColdDO.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrateColdLinks_withRecords_migratesSuccessfully() {
        when(coldDataProperties.getEnabled()).thenReturn(true);
        when(coldDataProperties.getDays()).thenReturn(90);
        when(coldDataProperties.getBatchSize()).thenReturn(200);

        ShortLinkDO record = ShortLinkDO.builder()
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
                .gid("g1").fullShortUrl("test.cn/old").build();
        when(shortLinkGoToMapper.selectOne(any())).thenReturn(goTo);
        when(shortLinkGoToColdMapper.selectCount(any())).thenReturn(0L);

        migrationJob.migrateColdLinks();

        verify(shortLinkColdMapper).insert(any(ShortLinkColdDO.class));
        verify(shortLinkGoToColdMapper).insert(any(ShortLinkGoToColdDO.class));
        verify(shortLinkGoToMapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(shortLinkMapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(cacheProducer).sendMessage("test.cn/old");
        verify(shortLinkMetrics).recordColdMigrationBatch(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrateColdLinks_alreadyInCold_skipsDuplicate() {
        when(coldDataProperties.getEnabled()).thenReturn(true);
        when(coldDataProperties.getDays()).thenReturn(90);
        when(coldDataProperties.getBatchSize()).thenReturn(200);

        ShortLinkDO record = ShortLinkDO.builder()
                .gid("g1").fullShortUrl("test.cn/dup").build();

        Page<ShortLinkDO> firstPage = new Page<>();
        firstPage.setRecords(List.of(record));
        Page<ShortLinkDO> emptyPage = new Page<>();
        emptyPage.setRecords(Collections.emptyList());
        when(shortLinkMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(firstPage)
                .thenReturn(emptyPage);

        when(shortLinkColdMapper.selectCount(any())).thenReturn(1L);
        when(shortLinkGoToMapper.selectOne(any())).thenReturn(null);

        migrationJob.migrateColdLinks();

        verify(shortLinkColdMapper, never()).insert(any(ShortLinkColdDO.class));
        verify(shortLinkMapper).delete(any());
    }
}
