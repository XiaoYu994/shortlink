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

package com.xhy.shortlink.biz.statsservice.mq.consumer;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.biz.statsservice.common.enums.OrderTagEnum;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessLogsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkBrowserStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkDeviceStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkNetworkStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkOsStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessLogsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkBrowserStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkDeviceStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkNetworkStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkOsStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.statsservice.locale.AccessLocaleEnricher;
import com.xhy.shortlink.biz.statsservice.locale.IpLocation;
import com.xhy.shortlink.biz.statsservice.locale.IpLocationService;
import com.xhy.shortlink.biz.statsservice.metrics.StatsMetrics;
import com.xhy.shortlink.biz.statsservice.mq.event.ShortLinkStatsRecordEvent;
import com.xhy.shortlink.framework.starter.idempotent.annotation.Idempotent;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentSceneEnum;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.*;
import static com.xhy.shortlink.biz.statsservice.common.constant.RocketMQConstant.STATS_RECORD_GROUP;
import static com.xhy.shortlink.biz.statsservice.common.constant.RocketMQConstant.STATS_RECORD_TOPIC;
import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.TODAY_EXPIRETIME;

/**
 * 短链接统计数据保存消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = STATS_RECORD_TOPIC, consumerGroup = STATS_RECORD_GROUP)
public class ShortLinkStatsSaveConsumer implements RocketMQListener<ShortLinkStatsRecordEvent> {

    private static final long REDIS_KEY_NOT_EXISTS = -1L;

    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final StatsMetrics statsMetrics;
    private final IpLocationService ipLocationService;
    private final AccessLocaleEnricher accessLocaleEnricher;

    /**
     * 主统计入库受 MQ 幂等保护。地区补全在全部写库成功之后才入队，
     * 不在幂等键内：丢弃/失败只落「未知」省份，不会回补 PV。
     */
    @Override
    @Idempotent(
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            key = "#event.eventId",
            uniqueKeyPrefix = "stats-save:",
            keyTimeout = 7200
    )
    public void onMessage(ShortLinkStatsRecordEvent event) {
        long startNanos = System.nanoTime();
        try {
            actualSaveShortLinkStats(event);
            statsMetrics.recordConsumeSuccess(Duration.ofNanos(System.nanoTime() - startNanos));
        } catch (RuntimeException ex) {
            statsMetrics.recordConsumeFailure(Duration.ofNanos(System.nanoTime() - startNanos));
            throw ex;
        }
    }

    private void actualSaveShortLinkStats(ShortLinkStatsRecordEvent statsRecord) {
        String fullShortUrl = statsRecord.getFullShortUrl();
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(
                String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();
        rLock.lock();
        try {
            String todayStr = DateUtil.formatDate(statsRecord.getCurrentDate());
            String gid = statsRecord.getGid();
            if (StrUtil.isBlank(gid)) {
                ShortLinkGoToDO gotoDO = shortLinkGoToMapper.selectOne(
                        Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                                .eq(ShortLinkGoToDO::getFullShortUrl, fullShortUrl));
                gid = gotoDO.getGid();
            }

            // Redis 统计
            String pvRankKey = String.format(RANK_KEY,
                    OrderTagEnum.TODAY_PV.getValue(), gid, todayStr);
            stringRedisTemplate.opsForZSet().incrementScore(pvRankKey, fullShortUrl, 1);
            if (stringRedisTemplate.getExpire(pvRankKey) == REDIS_KEY_NOT_EXISTS) {
                stringRedisTemplate.expire(pvRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }

            String todayUvHllKey = String.format(TODAY_UV_HLL_KEY, fullShortUrl, todayStr);
            Long uvAddedToday = stringRedisTemplate.opsForHyperLogLog()
                    .add(todayUvHllKey, statsRecord.getUv());
            if (stringRedisTemplate.getExpire(todayUvHllKey) == REDIS_KEY_NOT_EXISTS) {
                stringRedisTemplate.expire(todayUvHllKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }
            long todayUvCount = stringRedisTemplate.opsForHyperLogLog().size(todayUvHllKey);
            String uvRankKey = String.format(RANK_KEY,
                    OrderTagEnum.TODAY_UV.getValue(), gid, todayStr);
            stringRedisTemplate.opsForZSet().add(uvRankKey, fullShortUrl, todayUvCount);
            if (stringRedisTemplate.getExpire(uvRankKey) == REDIS_KEY_NOT_EXISTS) {
                stringRedisTemplate.expire(uvRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }

            String totalUvHllKey = String.format(TOTAL_UV_HLL_KEY, fullShortUrl);
            Long uvAddedTotal = stringRedisTemplate.opsForHyperLogLog()
                    .add(totalUvHllKey, statsRecord.getUv());

            String todayUipHllKey = String.format(TODAY_UIP_HLL_KEY, fullShortUrl, todayStr);
            Long uipAddedToday = stringRedisTemplate.opsForHyperLogLog()
                    .add(todayUipHllKey, statsRecord.getRemoteAddr());
            if (stringRedisTemplate.getExpire(todayUipHllKey) == REDIS_KEY_NOT_EXISTS) {
                stringRedisTemplate.expire(todayUipHllKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }
            long todayUipCount = stringRedisTemplate.opsForHyperLogLog().size(todayUipHllKey);
            String uipRankKey = String.format(RANK_KEY,
                    OrderTagEnum.TODAY_UIP.getValue(), gid, todayStr);
            stringRedisTemplate.opsForZSet().add(uipRankKey, fullShortUrl, todayUipCount);
            if (stringRedisTemplate.getExpire(uipRankKey) == REDIS_KEY_NOT_EXISTS) {
                stringRedisTemplate.expire(uipRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }

            String totalUipHllKey = String.format(TOTAL_UIP_HLL_KEY, fullShortUrl);
            Long uipAddedTotal = stringRedisTemplate.opsForHyperLogLog()
                    .add(totalUipHllKey, statsRecord.getRemoteAddr());

            // 数据库统计
            saveStatsToDatabase(statsRecord, fullShortUrl, gid,
                    new StatsDelta(uvAddedToday == 1, uipAddedToday == 1,
                            uvAddedTotal == 1, uipAddedTotal == 1));
        } finally {
            rLock.unlock();
        }
    }

    private void saveStatsToDatabase(ShortLinkStatsRecordEvent statsRecord,
                                     String fullShortUrl, String gid,
                                     StatsDelta delta) {
        Date currentDate = statsRecord.getCurrentDate();
        int hour = DateUtil.hour(currentDate, true);
        int weekday = DateUtil.dayOfWeekEnum(currentDate).getIso8601Value();
        String remoteAddr = statsRecord.getRemoteAddr();

        Optional<IpLocation> peeked = ipLocationService.peekWithoutHttp(remoteAddr);
        IpLocation syncLocation = peeked.orElse(IpLocation.unknown());

        // OS 统计
        linkOsStatsMapper.shortLinkOsState(LinkOsStatsDO.builder()
                .fullShortUrl(fullShortUrl).os(statsRecord.getOs())
                .cnt(1).date(currentDate).build());

        // 浏览器统计
        linkBrowserStatsMapper.shortLinkBrowserState(LinkBrowserStatsDO.builder()
                .fullShortUrl(fullShortUrl).browser(statsRecord.getBrowser())
                .cnt(1).date(currentDate).build());

        // 设备统计
        linkDeviceStatsMapper.shortLinkDeviceState(LinkDeviceStatsDO.builder()
                .fullShortUrl(fullShortUrl).device(statsRecord.getDevice())
                .cnt(1).date(currentDate).build());

        // 网络统计
        linkNetworkStatsMapper.shortLinkNetworkState(LinkNetworkStatsDO.builder()
                .fullShortUrl(fullShortUrl).network(statsRecord.getNetwork())
                .cnt(1).date(currentDate).build());

        // 访问日志
        LinkAccessLogsDO accessLog = LinkAccessLogsDO.builder()
                .fullShortUrl(fullShortUrl).ip(remoteAddr).user(statsRecord.getUv())
                .os(statsRecord.getOs()).browser(statsRecord.getBrowser())
                .device(statsRecord.getDevice()).network(statsRecord.getNetwork())
                .locale(syncLocation.display()).build();
        linkAccessLogsMapper.insert(accessLog);

        // 基础访问统计
        linkAccessStatsMapper.shortLinkStats(Collections.singletonList(
                LinkAccessStatsDO.builder()
                        .fullShortUrl(fullShortUrl).date(currentDate)
                        .hour(hour).weekday(weekday)
                        .pv(1).uv(delta.todayNewUv() ? 1 : 0).uip(delta.todayNewUip() ? 1 : 0)
                        .build()));

        // 主表/冷表总数自增
        int affected = shortLinkMapper.incrementStats(gid, fullShortUrl,
                1, delta.totalNewUv() ? 1 : 0, delta.totalNewUip() ? 1 : 0);
        if (affected == 0) {
            shortLinkColdMapper.incrementStats(gid, fullShortUrl,
                    1, delta.totalNewUv() ? 1 : 0, delta.totalNewUip() ? 1 : 0);
        }

        if (peeked.isPresent()) {
            accessLocaleEnricher.persistLocale(fullShortUrl, currentDate, peeked.get());
        } else {
            accessLocaleEnricher.submit(fullShortUrl, currentDate, remoteAddr, accessLog.getId());
        }
    }

    /** 数据库统计需要的 UV/UIP 增量标记。 */
    private record StatsDelta(boolean todayNewUv, boolean todayNewUip,
                              boolean totalNewUv, boolean totalNewUip) {
    }
}
