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

package com.xhy.shortlink.project.test;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.project.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToColdDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.project.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.project.job.ShortLinkColdMigrationJob;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;


/*
*  验证系统能否自动将长时间未访问/创建的“老旧短链接”从热库迁移到冷库。
* */
public class ShortLinkColdMigrationJobTest {

    @Test
    public void testMigrateColdLinks() {
        ShortLinkMapper shortLinkMapper = Mockito.mock(ShortLinkMapper.class);
        ShortLinkGoToMapper shortLinkGoToMapper = Mockito.mock(ShortLinkGoToMapper.class);
        ShortLinkColdMapper shortLinkColdMapper = Mockito.mock(ShortLinkColdMapper.class);
        ShortLinkGoToColdMapper shortLinkGoToColdMapper = Mockito.mock(ShortLinkGoToColdMapper.class);
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ShortLinkMessageProducer<String> cacheProducer = Mockito.mock(ShortLinkMessageProducer.class);

        ShortLinkColdMigrationJob job = new ShortLinkColdMigrationJob(
                shortLinkMapper,
                shortLinkGoToMapper,
                shortLinkColdMapper,
                shortLinkGoToColdMapper,
                stringRedisTemplate,
                cacheProducer
        );
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "coldDays", 90);
        ReflectionTestUtils.setField(job, "batchSize", 10);

        ShortLinkDO record = new ShortLinkDO();
        record.setGid("g1");
        record.setFullShortUrl("nurl.ink/cold");
        record.setCreateTime(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 100));

        Page<ShortLinkDO> page1 = new Page<>(1, 10);
        page1.setRecords(Collections.singletonList(record));
        Page<ShortLinkDO> page2 = new Page<>(1, 10);
        page2.setRecords(Collections.emptyList());

        Mockito.when(shortLinkMapper.selectPage(any(), any())).thenReturn(page1).thenReturn(page2);
        Mockito.when(shortLinkColdMapper.selectCount(any())).thenReturn(0L);

        ShortLinkGoToDO goToDO = new ShortLinkGoToDO();
        goToDO.setGid("g1");
        goToDO.setFullShortUrl("nurl.ink/cold");
        Mockito.when(shortLinkGoToMapper.selectOne(any())).thenReturn(goToDO);
        Mockito.when(shortLinkGoToColdMapper.selectCount(any())).thenReturn(0L);

        job.migrateColdLinks();

        Mockito.verify(shortLinkColdMapper, Mockito.times(1)).insert((ShortLinkColdDO) any());
        Mockito.verify(shortLinkGoToColdMapper, Mockito.times(1)).insert((ShortLinkGoToColdDO) any());
        Mockito.verify(shortLinkMapper, Mockito.times(1)).deletePhysical(eq("g1"), eq("nurl.ink/cold"));
    }
}
