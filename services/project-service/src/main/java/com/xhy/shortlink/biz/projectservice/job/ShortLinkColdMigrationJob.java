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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkCacheProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 冷数据定时迁移任务
 * <p>
 * 每日凌晨扫描热表，将超过指定天数未访问的链接迁移到冷库。
 *
 * @author XiaoYu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ColdDataProperties.class)
public class ShortLinkColdMigrationJob {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final ShortLinkCacheProducer cacheProducer;
    private final ColdDataProperties coldDataProperties;

    @Scheduled(cron = "${short-link.cold-data.cron:0 30 2 * * ?}")
    public void migrateColdLinks() {
        if (!coldDataProperties.getEnabled()) {
            return;
        }
        Date threshold = DateUtil.offsetDay(new Date(), -coldDataProperties.getDays());
        int batchSize = coldDataProperties.getBatchSize();
        int totalMigrated = 0;

        // 分批扫描热表中不活跃的链接
        while (true) {
            LambdaQueryWrapper<ShortLinkDO> wrapper = Wrappers.<ShortLinkDO>lambdaQuery()
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .and(q -> q.lt(ShortLinkDO::getLastAccessTime, threshold)
                            .or()
                            .and(inner -> inner.isNull(ShortLinkDO::getLastAccessTime)
                                    .lt(ShortLinkDO::getCreateTime, threshold)))
                    .orderByAsc(ShortLinkDO::getLastAccessTime);
            List<ShortLinkDO> records = shortLinkMapper.selectPage(new Page<>(1, batchSize), wrapper).getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            for (ShortLinkDO record : records) {
                if (migrateSingle(record)) {
                    totalMigrated++;
                }
            }
        }
        if (totalMigrated > 0) {
            log.info("[冷数据迁移] 完成，共迁移 {} 条", totalMigrated);
        }
    }

    /**
     * 单条迁移：复制到冷表 → 删除热表 → 清缓存
     */
    private boolean migrateSingle(ShortLinkDO record) {
        try {
            // 冷表去重检查
            boolean existsInCold = shortLinkColdMapper.selectCount(Wrappers.<ShortLinkColdDO>lambdaQuery()
                    .eq(ShortLinkColdDO::getGid, record.getGid())
                    .eq(ShortLinkColdDO::getFullShortUrl, record.getFullShortUrl())) > 0;
            if (!existsInCold) {
                shortLinkColdMapper.insert(BeanUtil.toBean(record, ShortLinkColdDO.class));
            }

            // 迁移路由表
            ShortLinkGoToDO goTo = shortLinkGoToMapper.selectOne(Wrappers.<ShortLinkGoToDO>lambdaQuery()
                    .eq(ShortLinkGoToDO::getFullShortUrl, record.getFullShortUrl()));
            if (goTo != null) {
                boolean gotoExists = shortLinkGoToColdMapper.selectCount(Wrappers.<ShortLinkGoToColdDO>lambdaQuery()
                        .eq(ShortLinkGoToColdDO::getFullShortUrl, goTo.getFullShortUrl())) > 0;
                if (!gotoExists) {
                    shortLinkGoToColdMapper.insert(BeanUtil.toBean(goTo, ShortLinkGoToColdDO.class));
                }
                shortLinkGoToMapper.delete(Wrappers.<ShortLinkGoToDO>lambdaQuery()
                        .eq(ShortLinkGoToDO::getFullShortUrl, record.getFullShortUrl()));
            }

            // 删除热表记录
            shortLinkMapper.delete(Wrappers.<ShortLinkDO>lambdaQuery()
                    .eq(ShortLinkDO::getGid, record.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, record.getFullShortUrl()));

            // 清除缓存
            cacheProducer.sendMessage(record.getFullShortUrl());
            return true;
        } catch (Exception e) {
            log.error("[冷数据迁移] 失败，fullShortUrl={}", record.getFullShortUrl(), e);
            return false;
        }
    }
}
