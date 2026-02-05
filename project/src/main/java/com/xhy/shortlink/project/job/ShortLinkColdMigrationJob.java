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

package com.xhy.shortlink.project.job;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.project.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToColdDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.project.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkColdMigrationJob {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMessageProducer<String> cacheProducer;

    @Value("${short-link.cold-data.days:90}")
    private int coldDays;

    @Value("${short-link.cold-data.batch-size:200}")
    private int batchSize;

    @Value("${short-link.cold-data.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${short-link.cold-data.cron:0 30 2 * * ?}")
    public void migrateColdLinks() {
        if (!enabled) {
            return;
        }
        Date threshold = DateUtil.offsetDay(new Date(), -coldDays);
        while (true) {
            Page<ShortLinkDO> page = new Page<>(1, batchSize);
            LambdaQueryWrapper<ShortLinkDO> wrapper = new LambdaQueryWrapper<ShortLinkDO>()
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .and(q -> q.lt(ShortLinkDO::getLastAccessTime, threshold)
                            .or()
                            .and(inner -> inner.isNull(ShortLinkDO::getLastAccessTime)
                                    .lt(ShortLinkDO::getCreateTime, threshold)))
                    .orderByAsc(ShortLinkDO::getLastAccessTime);
            Page<ShortLinkDO> result = shortLinkMapper.selectPage(page, wrapper);
            List<ShortLinkDO> records = result.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            for (ShortLinkDO record : records) {
                migrateSingle(record);
            }
        }
    }

    private void migrateSingle(ShortLinkDO record) {
        try {
            boolean existsInCold = shortLinkColdMapper.selectCount(new LambdaQueryWrapper<ShortLinkColdDO>()
                    .eq(ShortLinkColdDO::getGid, record.getGid())
                    .eq(ShortLinkColdDO::getFullShortUrl, record.getFullShortUrl())) > 0;
            if (!existsInCold) {
                shortLinkColdMapper.insert(BeanUtil.toBean(record, ShortLinkColdDO.class));
            }

            ShortLinkGoToDO goToDO = shortLinkGoToMapper.selectOne(new LambdaQueryWrapper<ShortLinkGoToDO>()
                    .eq(ShortLinkGoToDO::getFullShortUrl, record.getFullShortUrl()));
            if (goToDO != null) {
                boolean gotoExists = shortLinkGoToColdMapper.selectCount(new LambdaQueryWrapper<ShortLinkGoToColdDO>()
                        .eq(ShortLinkGoToColdDO::getFullShortUrl, goToDO.getFullShortUrl())) > 0;
                if (!gotoExists) {
                    shortLinkGoToColdMapper.insert(BeanUtil.toBean(goToDO, ShortLinkGoToColdDO.class));
                }
            }

            shortLinkMapper.deletePhysical(record.getGid(), record.getFullShortUrl());
            shortLinkGoToMapper.delete(new LambdaQueryWrapper<ShortLinkGoToDO>()
                    .eq(ShortLinkGoToDO::getFullShortUrl, record.getFullShortUrl()));

            clearCache(record.getFullShortUrl());
        } catch (Exception e) {
            log.error("冷数据迁移失败，fullShortUrl={}", record.getFullShortUrl(), e);
        }
    }

    private void clearCache(String fullShortUrl) {
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        cacheProducer.send(fullShortUrl);
    }
}
