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

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.xhy.shortlink.biz.projectservice.helper.ShortLinkCacheHelper;
import com.xhy.shortlink.biz.projectservice.metrics.ShortLinkMetrics;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkColdDataService;
import com.xhy.shortlink.framework.starter.cache.toolkit.RedisIncrWithExpire;
import com.xhy.shortlink.framework.starter.common.toolkit.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.LOCK_GOTO_SHORT_LINK_KEY;
import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.SHORT_LINK_COLD_MIGRATION_LOCK_KEY;
import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.SHORT_LINK_COLD_REHOT_KEY;

/**
 * 冷热数据管理实现
 *
 * @author XiaoYu
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ColdDataProperties.class)
public class ShortLinkColdDataServiceImpl implements ShortLinkColdDataService {

    /** 回温计数器过期天数 */
    private static final int REHOT_COUNTER_EXPIRE_DAYS = 7;

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final ShortLinkCacheHelper cacheHelper;
    private final ColdDataProperties coldDataProperties;
    private final ShortLinkMetrics shortLinkMetrics;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;

    @Value("${short-link.domain.protocol:http}")
    private String domainProtocol = "http";

    @Override
    public List<ShortLinkPageRespDTO> mergeSorted(
            List<ShortLinkDO> hotList,
            List<ShortLinkColdDO> coldList,
            String orderTag,
            Consumer<ShortLinkPageRespDTO> afterConvert) {
        List<ShortLinkPageRespDTO> hotDtos = convertHot(hotList, afterConvert);
        List<ShortLinkPageRespDTO> coldDtos = convertCold(coldList, afterConvert);
        Comparator<ShortLinkPageRespDTO> comparator = buildOrderComparator(orderTag);
        // 库内排序与比较器可能不一致（NULL、并列 createTime），先按同一比较器排再归并。
        hotDtos.sort(comparator);
        coldDtos.sort(comparator);
        return mergeByComparator(hotDtos, coldDtos, comparator);
    }

    @Override
    public void tryRehot(String fullShortUrl, String gid) {
        try {
            String key = String.format(SHORT_LINK_COLD_REHOT_KEY, fullShortUrl);
            Long count = RedisIncrWithExpire.increment(
                    stringRedisTemplate, key, TimeUnit.DAYS.toSeconds(REHOT_COUNTER_EXPIRE_DAYS));
            if (count != null && count >= coldDataProperties.getRehot().getThreshold()) {
                withLinkLock(fullShortUrl, () -> {
                    boolean moved = executeInTransaction(() -> rehotColdLink(fullShortUrl, gid));
                    if (moved) {
                        stringRedisTemplate.delete(key);
                    }
                    return null;
                });
            }
        } catch (Exception e) {
            log.error("[回温] 失败，fullShortUrl={}", fullShortUrl, e);
        }
    }

    @Override
    public int migrateInactiveLinks() {
        if (!Boolean.TRUE.equals(coldDataProperties.getEnabled())) {
            return 0;
        }
        RLock migrationLock = redissonClient.getLock(SHORT_LINK_COLD_MIGRATION_LOCK_KEY);
        migrationLock.lock();
        try {
            return doMigrateInactiveLinks();
        } finally {
            migrationLock.unlock();
        }
    }

    private int doMigrateInactiveLinks() {
        Date threshold = DateUtil.offsetDay(new Date(), -coldDataProperties.getDays());
        int batchSize = coldDataProperties.getBatchSize();
        int totalMigrated = 0;

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
            shortLinkMetrics.recordColdMigrationBatch(records.size());
            int batchMigrated = 0;
            for (ShortLinkDO record : records) {
                if (migrateSingle(record)) {
                    batchMigrated++;
                    totalMigrated++;
                }
            }
            if (batchMigrated == 0) {
                log.error("[冷数据迁移] 本批全部失败，中止以免空转，batchSize={}", records.size());
                break;
            }
        }
        if (totalMigrated > 0) {
            log.info("[冷数据迁移] 完成，共迁移 {} 条", totalMigrated);
        }
        return totalMigrated;
    }

    private List<ShortLinkPageRespDTO> convertHot(
            List<ShortLinkDO> hotList, Consumer<ShortLinkPageRespDTO> afterConvert) {
        List<ShortLinkPageRespDTO> result = new ArrayList<>();
        if (hotList == null) {
            return result;
        }
        for (ShortLinkDO hot : hotList) {
            ShortLinkPageRespDTO dto = BeanUtil.convert(hot, ShortLinkPageRespDTO.class);
            dto.setTotalPv(toInteger(hot.getTotalPv()));
            prefixDomain(dto);
            if (afterConvert != null) {
                afterConvert.accept(dto);
            }
            result.add(dto);
        }
        return result;
    }

    private List<ShortLinkPageRespDTO> convertCold(
            List<ShortLinkColdDO> coldList, Consumer<ShortLinkPageRespDTO> afterConvert) {
        List<ShortLinkPageRespDTO> result = new ArrayList<>();
        if (coldList == null) {
            return result;
        }
        for (ShortLinkColdDO cold : coldList) {
            ShortLinkPageRespDTO dto = BeanUtil.convert(cold, ShortLinkPageRespDTO.class);
            dto.setTotalPv(toInteger(cold.getTotalPv()));
            prefixDomain(dto);
            if (afterConvert != null) {
                afterConvert.accept(dto);
            }
            result.add(dto);
        }
        return result;
    }

    private Integer toInteger(Long value) {
        return value == null ? null : value.intValue();
    }

    private void prefixDomain(ShortLinkPageRespDTO dto) {
        if (dto.getDomain() != null && !dto.getDomain().contains("://")) {
            dto.setDomain(domainProtocol + "://" + dto.getDomain());
        }
    }

    private List<ShortLinkPageRespDTO> mergeByComparator(
            List<ShortLinkPageRespDTO> left,
            List<ShortLinkPageRespDTO> right,
            Comparator<ShortLinkPageRespDTO> comparator) {
        List<ShortLinkPageRespDTO> merged = new ArrayList<>(left.size() + right.size());
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            if (comparator.compare(left.get(i), right.get(j)) <= 0) {
                merged.add(left.get(i++));
            } else {
                merged.add(right.get(j++));
            }
        }
        while (i < left.size()) {
            merged.add(left.get(i++));
        }
        while (j < right.size()) {
            merged.add(right.get(j++));
        }
        return merged;
    }

    private Comparator<ShortLinkPageRespDTO> buildOrderComparator(String orderTag) {
        // 与 MySQL DESC 一致：非空降序，NULL 在后，并列再按 createTime DESC。
        Comparator<Date> createTimeDesc = Comparator.nullsLast(Comparator.reverseOrder());
        if (CharSequenceUtil.equals(orderTag, "totalPv")) {
            return Comparator.comparing(ShortLinkPageRespDTO::getTotalPv,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ShortLinkPageRespDTO::getCreateTime, createTimeDesc);
        }
        if (CharSequenceUtil.equals(orderTag, "totalUv")) {
            return Comparator.comparing(ShortLinkPageRespDTO::getTotalUv,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ShortLinkPageRespDTO::getCreateTime, createTimeDesc);
        }
        if (CharSequenceUtil.equals(orderTag, "totalUip")) {
            return Comparator.comparing(ShortLinkPageRespDTO::getTotalUip,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ShortLinkPageRespDTO::getCreateTime, createTimeDesc);
        }
        return Comparator.comparing(ShortLinkPageRespDTO::getCreateTime, createTimeDesc);
    }

    private boolean rehotColdLink(String fullShortUrl, String gid) {
        ShortLinkColdDO coldDO = shortLinkColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                .eq(ShortLinkColdDO::getGid, gid)
                .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl));
        if (coldDO == null) {
            return true;
        }
        ShortLinkGoToColdDO goToCold = shortLinkGoToColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
        if (goToCold == null) {
            log.warn("[回温] 冷库路由缺失，保留冷库详情，fullShortUrl={}", fullShortUrl);
            return false;
        }
        ShortLinkDO hotDO = shortLinkMapper.selectOne(Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, gid)
                .eq(ShortLinkDO::getFullShortUrl, fullShortUrl));
        if (hotDO == null) {
            ShortLinkDO hotLink = BeanUtil.convert(coldDO, ShortLinkDO.class);
            // 热、冷库使用独立自增序列，跨表搬运不能复用源表主键。
            hotLink.setId(null);
            shortLinkMapper.insert(hotLink);
        }
        ShortLinkGoToDO hotGoTo = shortLinkGoToMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                .eq(ShortLinkGoToDO::getFullShortUrl, fullShortUrl));
        if (hotGoTo == null) {
            ShortLinkGoToDO hotRoute = BeanUtil.convert(goToCold, ShortLinkGoToDO.class);
            hotRoute.setId(null);
            shortLinkGoToMapper.insert(hotRoute);
        }
        shortLinkGoToColdMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
        shortLinkColdMapper.delete(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                .eq(ShortLinkColdDO::getGid, gid)
                .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl));
        log.info("[回温] 完成，fullShortUrl={}", fullShortUrl);
        return true;
    }

    private boolean migrateSingle(ShortLinkDO record) {
        try {
            return withLinkLock(record.getFullShortUrl(), () -> {
                boolean migrated = executeInTransaction(() -> migrateSingleInternal(record));
                if (migrated) {
                    cacheHelper.invalidate(record.getFullShortUrl());
                } else {
                    shortLinkMetrics.recordColdMigrationFailure();
                }
                return migrated;
            });
        } catch (Exception e) {
            log.error("[冷数据迁移] 失败，fullShortUrl={}", record.getFullShortUrl(), e);
            shortLinkMetrics.recordColdMigrationFailure();
            return false;
        }
    }

    private boolean migrateSingleInternal(ShortLinkDO record) {
        ShortLinkGoToDO goTo = shortLinkGoToMapper.selectOne(Wrappers.<ShortLinkGoToDO>lambdaQuery()
                .eq(ShortLinkGoToDO::getFullShortUrl, record.getFullShortUrl()));
        if (goTo == null) {
            long coldRouteCount = shortLinkGoToColdMapper.selectCount(
                    Wrappers.<ShortLinkGoToColdDO>lambdaQuery()
                            .eq(ShortLinkGoToColdDO::getFullShortUrl, record.getFullShortUrl()));
            if (coldRouteCount == 0) {
                log.warn("[冷数据迁移] 热库路由缺失，保留热库详情，fullShortUrl={}", record.getFullShortUrl());
                return false;
            }
        }
        boolean existsInCold = shortLinkColdMapper.selectCount(Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, record.getGid())
                .eq(ShortLinkColdDO::getFullShortUrl, record.getFullShortUrl())) > 0;
        if (!existsInCold) {
            ShortLinkColdDO coldLink = BeanUtil.convert(record, ShortLinkColdDO.class);
            // 热、冷库使用独立自增序列，跨表搬运不能复用源表主键。
            coldLink.setId(null);
            shortLinkColdMapper.insert(coldLink);
        }

        if (goTo != null) {
            boolean gotoExists = shortLinkGoToColdMapper.selectCount(Wrappers.<ShortLinkGoToColdDO>lambdaQuery()
                    .eq(ShortLinkGoToColdDO::getFullShortUrl, goTo.getFullShortUrl())) > 0;
            if (!gotoExists) {
                ShortLinkGoToColdDO coldRoute = BeanUtil.convert(goTo, ShortLinkGoToColdDO.class);
                coldRoute.setId(null);
                shortLinkGoToColdMapper.insert(coldRoute);
            }
            shortLinkGoToMapper.delete(Wrappers.<ShortLinkGoToDO>lambdaQuery()
                    .eq(ShortLinkGoToDO::getFullShortUrl, record.getFullShortUrl()));
        }

        shortLinkMapper.delete(Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, record.getGid())
                .eq(ShortLinkDO::getFullShortUrl, record.getFullShortUrl()));
        return true;
    }

    private <T> T withLinkLock(String fullShortUrl, Supplier<T> action) {
        RLock linkLock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        linkLock.lock();
        try {
            return action.get();
        } finally {
            linkLock.unlock();
        }
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
