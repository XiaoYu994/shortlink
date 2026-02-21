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
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.biz.statsservice.common.enums.OrderTagEnum;
import com.xhy.shortlink.biz.statsservice.dao.entity.*;
import com.xhy.shortlink.biz.statsservice.dao.mapper.*;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.*;
import static com.xhy.shortlink.biz.statsservice.common.constant.RocketMQConstant.STATS_RECORD_GROUP;
import static com.xhy.shortlink.biz.statsservice.common.constant.RocketMQConstant.STATS_RECORD_TOPIC;
import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.*;

/**
 * 短链接统计数据保存消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = STATS_RECORD_TOPIC, consumerGroup = STATS_RECORD_GROUP)
public class ShortLinkStatsSaveConsumer implements RocketMQListener<ShortLinkStatsRecordEvent> {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    @Override
    @Idempotent(
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            key = "#event.eventId",
            uniqueKeyPrefix = "stats-save:",
            keyTimeout = 7200
    )
    public void onMessage(ShortLinkStatsRecordEvent event) {
        actualSaveShortLinkStats(event);
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
            if (stringRedisTemplate.getExpire(pvRankKey) == -1) {
                stringRedisTemplate.expire(pvRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }

            String todayUvHllKey = String.format(TODAY_UV_HLL_KEY, fullShortUrl, todayStr);
            Long uvAddedToday = stringRedisTemplate.opsForHyperLogLog()
                    .add(todayUvHllKey, statsRecord.getUv());
            if (stringRedisTemplate.getExpire(todayUvHllKey) == -1) {
                stringRedisTemplate.expire(todayUvHllKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }
            long todayUvCount = stringRedisTemplate.opsForHyperLogLog().size(todayUvHllKey);
            String uvRankKey = String.format(RANK_KEY,
                    OrderTagEnum.TODAY_UV.getValue(), gid, todayStr);
            stringRedisTemplate.opsForZSet().add(uvRankKey, fullShortUrl, todayUvCount);
            if (stringRedisTemplate.getExpire(uvRankKey) == -1) {
                stringRedisTemplate.expire(uvRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }

            String totalUvHllKey = String.format(TOTAL_UV_HLL_KEY, fullShortUrl);
            Long uvAddedTotal = stringRedisTemplate.opsForHyperLogLog()
                    .add(totalUvHllKey, statsRecord.getUv());

            String todayUipHllKey = String.format(TODAY_UIP_HLL_KEY, fullShortUrl, todayStr);
            Long uipAddedToday = stringRedisTemplate.opsForHyperLogLog()
                    .add(todayUipHllKey, statsRecord.getRemoteAddr());
            if (stringRedisTemplate.getExpire(todayUipHllKey) == -1) {
                stringRedisTemplate.expire(todayUipHllKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }
            long todayUipCount = stringRedisTemplate.opsForHyperLogLog().size(todayUipHllKey);
            String uipRankKey = String.format(RANK_KEY,
                    OrderTagEnum.TODAY_UIP.getValue(), gid, todayStr);
            stringRedisTemplate.opsForZSet().add(uipRankKey, fullShortUrl, todayUipCount);
            if (stringRedisTemplate.getExpire(uipRankKey) == -1) {
                stringRedisTemplate.expire(uipRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }

            String totalUipHllKey = String.format(TOTAL_UIP_HLL_KEY, fullShortUrl);
            Long uipAddedTotal = stringRedisTemplate.opsForHyperLogLog()
                    .add(totalUipHllKey, statsRecord.getRemoteAddr());

            // 数据库统计
            saveStatsToDatabase(statsRecord, fullShortUrl, gid,
                    uvAddedToday == 1, uipAddedToday == 1,
                    uvAddedTotal == 1, uipAddedTotal == 1);
        } finally {
            rLock.unlock();
        }
    }

    private void saveStatsToDatabase(ShortLinkStatsRecordEvent statsRecord,
                                     String fullShortUrl, String gid,
                                     boolean isTodayNewUv, boolean isTodayNewUip,
                                     boolean isTotalNewUv, boolean isTotalNewUip) {
        Date currentDate = statsRecord.getCurrentDate();
        int hour = DateUtil.hour(currentDate, true);
        int weekday = DateUtil.dayOfWeekEnum(currentDate).getIso8601Value();
        String remoteAddr = statsRecord.getRemoteAddr();

        // IP 地理位置解析
        String actualProvince = LOCALE_UNKNOWN;
        String actualCity = LOCALE_UNKNOWN;
        String adcode = LOCALE_UNKNOWN;
        Map<String, Object> localeParamMap = new HashMap<>();
        localeParamMap.put("key", statsLocaleAmapKey);
        localeParamMap.put("ip", remoteAddr);
        try {
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap, 3000);
            JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            if (AMAP_SUCCESS_CODE.equals(localeResultObj.getString("infocode"))) {
                String province = localeResultObj.getString("province");
                if (StrUtil.isNotBlank(province) && !AMAP_EMPTY_VALUE.equals(province)) {
                    actualProvince = province;
                }
                String city = localeResultObj.getString("city");
                if (StrUtil.isNotBlank(city) && !AMAP_EMPTY_VALUE.equals(city)) {
                    actualCity = city;
                }
                String code = localeResultObj.getString("adcode");
                if (StrUtil.isNotBlank(code) && !AMAP_EMPTY_VALUE.equals(code)) {
                    adcode = code;
                }
            }
        } catch (Exception e) {
            log.warn("IP解析失败或超时, IP: {}", remoteAddr, e);
        }

        // 地区统计
        linkLocaleStatsMapper.shortLinkLocaleState(LinkLocaleStatsDO.builder()
                .fullShortUrl(fullShortUrl).province(actualProvince).city(actualCity)
                .adcode(adcode).cnt(1).country(LOCALE_COUNTRY_CN).date(currentDate).build());

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
        linkAccessLogsMapper.insert(LinkAccessLogsDO.builder()
                .fullShortUrl(fullShortUrl).ip(remoteAddr).user(statsRecord.getUv())
                .os(statsRecord.getOs()).browser(statsRecord.getBrowser())
                .device(statsRecord.getDevice()).network(statsRecord.getNetwork())
                .locale(StrUtil.join("-", LOCALE_COUNTRY_CN, actualProvince, actualCity)).build());

        // 基础访问统计
        linkAccessStatsMapper.shortLinkStats(Collections.singletonList(
                LinkAccessStatsDO.builder()
                        .fullShortUrl(fullShortUrl).date(currentDate)
                        .hour(hour).weekday(weekday)
                        .pv(1).uv(isTodayNewUv ? 1 : 0).uip(isTodayNewUip ? 1 : 0)
                        .build()));

        // 主表/冷表总数自增
        int affected = shortLinkMapper.incrementStats(gid, fullShortUrl,
                1, isTotalNewUv ? 1 : 0, isTotalNewUip ? 1 : 0);
        if (affected == 0) {
            shortLinkColdMapper.incrementStats(gid, fullShortUrl,
                    1, isTotalNewUv ? 1 : 0, isTotalNewUip ? 1 : 0);
        }
    }
}
