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
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.biz.statsservice.common.enums.OrderTagEnum;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessLogsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkBrowserStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkDeviceStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkLocaleStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkNetworkStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkOsStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessLogsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkBrowserStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkDeviceStatsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkLocaleStatsMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.LOCK_GID_UPDATE_KEY;
import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.RANK_KEY;
import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.TODAY_UIP_HLL_KEY;
import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.TODAY_UV_HLL_KEY;
import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.TOTAL_UIP_HLL_KEY;
import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.TOTAL_UV_HLL_KEY;
import static com.xhy.shortlink.biz.statsservice.common.constant.RocketMQConstant.STATS_RECORD_GROUP;
import static com.xhy.shortlink.biz.statsservice.common.constant.RocketMQConstant.STATS_RECORD_TOPIC;
import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.LOCALE_COUNTRY_CN;
import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.TODAY_EXPIRETIME;

/**
 * 统计消费：RocketMQ 批量拉取 + Redis pipeline + 维度内存聚合后写库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = STATS_RECORD_TOPIC,
        consumerGroup = STATS_RECORD_GROUP,
        consumeThreadNumber = 16,
        consumeThreadMax = 16)
public class ShortLinkStatsSaveConsumer implements RocketMQListener<ShortLinkStatsRecordEvent>,
        RocketMQPushConsumerLifecycleListener {

    private static final int BATCH_SIZE = 64;
    private static final int IDEMPOTENT_SECONDS = 7200;
    private static final String IDEMPOTENT_PREFIX = "stats-save:";

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
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final StatsMetrics statsMetrics;
    private final IpLocationService ipLocationService;
    private final AccessLocaleEnricher accessLocaleEnricher;

    @Override
    public void onMessage(ShortLinkStatsRecordEvent event) {
        consumeBatch(List.of(event));
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setConsumeMessageBatchMaxSize(BATCH_SIZE);
        consumer.setPullBatchSize(BATCH_SIZE);
        consumer.setConsumeThreadMin(16);
        consumer.setConsumeThreadMax(16);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            try {
                List<ShortLinkStatsRecordEvent> events = new ArrayList<>(messages.size());
                for (MessageExt message : messages) {
                    events.add(JSON.parseObject(new String(message.getBody(), StandardCharsets.UTF_8),
                            ShortLinkStatsRecordEvent.class));
                }
                consumeBatch(events);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (RuntimeException ex) {
                log.warn("批量消费统计消息失败, size={}", messages.size(), ex);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
    }

    public void consumeBatch(List<ShortLinkStatsRecordEvent> rawEvents) {
        long startNanos = System.nanoTime();
        List<String> claimedKeys = new ArrayList<>();
        try {
            List<ShortLinkStatsRecordEvent> events = new ArrayList<>();
            for (ShortLinkStatsRecordEvent event : rawEvents) {
                if (event == null || StrUtil.isBlank(event.getFullShortUrl())) {
                    continue;
                }
                if (claim(event.getEventId(), claimedKeys)) {
                    events.add(event);
                }
            }
            if (events.isEmpty()) {
                return;
            }
            persistBatch(events);
            statsMetrics.recordConsumeSuccess(Duration.ofNanos(System.nanoTime() - startNanos));
        } catch (RuntimeException ex) {
            release(claimedKeys);
            statsMetrics.recordConsumeFailure(Duration.ofNanos(System.nanoTime() - startNanos));
            throw ex;
        }
    }

    private void persistBatch(List<ShortLinkStatsRecordEvent> events) {
        Map<String, List<ShortLinkStatsRecordEvent>> byUrl = new LinkedHashMap<>();
        for (ShortLinkStatsRecordEvent event : events) {
            byUrl.computeIfAbsent(event.getFullShortUrl(), key -> new ArrayList<>()).add(event);
        }
        List<String> urls = new ArrayList<>(byUrl.keySet());
        urls.sort(Comparator.naturalOrder());

        List<LinkAccessLogsDO> logs = new ArrayList<>(events.size());
        Map<String, LinkAccessStatsDO> accessStats = new HashMap<>();
        Map<String, LinkOsStatsDO> osStats = new HashMap<>();
        Map<String, LinkBrowserStatsDO> browserStats = new HashMap<>();
        Map<String, LinkDeviceStatsDO> deviceStats = new HashMap<>();
        Map<String, LinkNetworkStatsDO> networkStats = new HashMap<>();
        Map<String, LinkLocaleStatsDO> localeStats = new HashMap<>();
        Map<String, int[]> linkTotals = new HashMap<>();
        Map<String, String> urlGid = new HashMap<>();
        List<ShortLinkStatsRecordEvent> publicIpEvents = new ArrayList<>();

        for (String url : urls) {
            List<ShortLinkStatsRecordEvent> group = byUrl.get(url);
            RLock lock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, url)).readLock();
            lock.lock();
            try {
                String gid = resolveGid(url, group.get(0).getGid());
                urlGid.put(url, gid);
                RedisDelta delta = applyRedis(url, gid, group);
                int[] totals = linkTotals.computeIfAbsent(url, key -> new int[3]);
                totals[0] += group.size();
                totals[1] += delta.uvNew();
                totals[2] += delta.uipNew();
                for (ShortLinkStatsRecordEvent event : group) {
                    accumulate(event, logs, accessStats, osStats, browserStats, deviceStats,
                            networkStats, localeStats, publicIpEvents);
                }
            } finally {
                lock.unlock();
            }
        }

        if (!logs.isEmpty()) {
            linkAccessLogsMapper.insertBatch(logs);
        }
        if (!accessStats.isEmpty()) {
            linkAccessStatsMapper.shortLinkStats(new ArrayList<>(accessStats.values()));
        }
        if (!osStats.isEmpty()) {
            linkOsStatsMapper.shortLinkOsStateBatch(new ArrayList<>(osStats.values()));
        }
        if (!browserStats.isEmpty()) {
            linkBrowserStatsMapper.shortLinkBrowserStateBatch(new ArrayList<>(browserStats.values()));
        }
        if (!deviceStats.isEmpty()) {
            linkDeviceStatsMapper.shortLinkDeviceStateBatch(new ArrayList<>(deviceStats.values()));
        }
        if (!networkStats.isEmpty()) {
            linkNetworkStatsMapper.shortLinkNetworkStateBatch(new ArrayList<>(networkStats.values()));
        }
        for (Map.Entry<String, int[]> entry : linkTotals.entrySet()) {
            String url = entry.getKey();
            String gid = urlGid.get(url);
            int[] totals = entry.getValue();
            int affected = shortLinkMapper.incrementStats(gid, url, totals[0], totals[1], totals[2]);
            if (affected == 0) {
                shortLinkColdMapper.incrementStats(gid, url, totals[0], totals[1], totals[2]);
            }
        }
        if (!localeStats.isEmpty()) {
            linkLocaleStatsMapper.shortLinkLocaleStateBatch(new ArrayList<>(localeStats.values()));
        }
        for (ShortLinkStatsRecordEvent event : publicIpEvents) {
            accessLocaleEnricher.submit(event.getFullShortUrl(), event.getCurrentDate(),
                    event.getRemoteAddr(), null);
        }
    }

    private void accumulate(ShortLinkStatsRecordEvent event,
                            List<LinkAccessLogsDO> logs,
                            Map<String, LinkAccessStatsDO> accessStats,
                            Map<String, LinkOsStatsDO> osStats,
                            Map<String, LinkBrowserStatsDO> browserStats,
                            Map<String, LinkDeviceStatsDO> deviceStats,
                            Map<String, LinkNetworkStatsDO> networkStats,
                            Map<String, LinkLocaleStatsDO> localeStats,
                            List<ShortLinkStatsRecordEvent> publicIpEvents) {
        Date currentDate = event.getCurrentDate() == null ? new Date() : event.getCurrentDate();
        String day = DateUtil.formatDate(currentDate);
        Date dayDate = DateUtil.parseDate(day);
        int hour = DateUtil.hour(currentDate, true);
        int weekday = DateUtil.dayOfWeekEnum(currentDate).getIso8601Value();
        String url = event.getFullShortUrl();

        Optional<IpLocation> peeked = ipLocationService.peekWithoutHttp(event.getRemoteAddr());
        IpLocation location = peeked.orElse(IpLocation.unknown());
        logs.add(LinkAccessLogsDO.builder()
                .fullShortUrl(url)
                .ip(event.getRemoteAddr())
                .user(event.getUv())
                .os(event.getOs())
                .browser(event.getBrowser())
                .device(event.getDevice())
                .network(event.getNetwork())
                .locale(location.display())
                .build());

        String accessKey = url + "|" + day + "|" + hour;
        accessStats.compute(accessKey, (key, existing) -> {
            if (existing == null) {
                return LinkAccessStatsDO.builder()
                        .fullShortUrl(url).date(dayDate).hour(hour).weekday(weekday)
                        .pv(1).uv(0).uip(0).build();
            }
            existing.setPv(existing.getPv() + 1);
            return existing;
        });
        bumpOs(osStats, url, dayDate, event.getOs());
        bumpBrowser(browserStats, url, dayDate, event.getBrowser());
        bumpDevice(deviceStats, url, dayDate, event.getDevice());
        bumpNetwork(networkStats, url, dayDate, event.getNetwork());
        if (peeked.isPresent()) {
            String localeKey = url + "|" + day + "|" + location.province() + "|" + location.city();
            localeStats.compute(localeKey, (key, existing) -> {
                if (existing == null) {
                    return LinkLocaleStatsDO.builder()
                            .fullShortUrl(url).date(dayDate).cnt(1)
                            .country(LOCALE_COUNTRY_CN)
                            .province(location.province())
                            .city(location.city())
                            .adcode(location.adcode())
                            .build();
                }
                existing.setCnt(existing.getCnt() + 1);
                return existing;
            });
        } else {
            publicIpEvents.add(event);
        }
    }

    private void bumpOs(Map<String, LinkOsStatsDO> stats, String url, Date day, String os) {
        String key = url + "|" + DateUtil.formatDate(day) + "|" + os;
        stats.compute(key, (k, existing) -> {
            if (existing == null) {
                return LinkOsStatsDO.builder().fullShortUrl(url).date(day).os(os).cnt(1).build();
            }
            existing.setCnt(existing.getCnt() + 1);
            return existing;
        });
    }

    private void bumpBrowser(Map<String, LinkBrowserStatsDO> stats, String url, Date day, String browser) {
        String key = url + "|" + DateUtil.formatDate(day) + "|" + browser;
        stats.compute(key, (k, existing) -> {
            if (existing == null) {
                return LinkBrowserStatsDO.builder().fullShortUrl(url).date(day).browser(browser).cnt(1).build();
            }
            existing.setCnt(existing.getCnt() + 1);
            return existing;
        });
    }

    private void bumpDevice(Map<String, LinkDeviceStatsDO> stats, String url, Date day, String device) {
        String key = url + "|" + DateUtil.formatDate(day) + "|" + device;
        stats.compute(key, (k, existing) -> {
            if (existing == null) {
                return LinkDeviceStatsDO.builder().fullShortUrl(url).date(day).device(device).cnt(1).build();
            }
            existing.setCnt(existing.getCnt() + 1);
            return existing;
        });
    }

    private void bumpNetwork(Map<String, LinkNetworkStatsDO> stats, String url, Date day, String network) {
        String key = url + "|" + DateUtil.formatDate(day) + "|" + network;
        stats.compute(key, (k, existing) -> {
            if (existing == null) {
                return LinkNetworkStatsDO.builder().fullShortUrl(url).date(day).network(network).cnt(1).build();
            }
            existing.setCnt(existing.getCnt() + 1);
            return existing;
        });
    }

    private RedisDelta applyRedis(String url, String gid, List<ShortLinkStatsRecordEvent> events) {
        String today = DateUtil.formatDate(events.get(0).getCurrentDate() == null
                ? new Date() : events.get(0).getCurrentDate());
        String pvRankKey = String.format(RANK_KEY, OrderTagEnum.TODAY_PV.getValue(), gid, today);
        String todayUvKey = String.format(TODAY_UV_HLL_KEY, url, today);
        String totalUvKey = String.format(TOTAL_UV_HLL_KEY, url);
        String todayUipKey = String.format(TODAY_UIP_HLL_KEY, url, today);
        String totalUipKey = String.format(TOTAL_UIP_HLL_KEY, url);
        String uvRankKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UV.getValue(), gid, today);
        String uipRankKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UIP.getValue(), gid, today);

        List<Object> first = stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) {
                operations.opsForZSet().incrementScore(pvRankKey, url, events.size());
                operations.expire(pvRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
                for (ShortLinkStatsRecordEvent event : events) {
                    operations.opsForHyperLogLog().add(todayUvKey, event.getUv());
                    operations.opsForHyperLogLog().add(totalUvKey, event.getUv());
                    operations.opsForHyperLogLog().add(todayUipKey, event.getRemoteAddr());
                    operations.opsForHyperLogLog().add(totalUipKey, event.getRemoteAddr());
                }
                operations.expire(todayUvKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
                operations.expire(todayUipKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
                return null;
            }
        });
        int uvNew = 0;
        int uipNew = 0;
        int cursor = 2;
        for (int i = 0; i < events.size(); i++) {
            uvNew += longVal(first, cursor++) == 1 ? 1 : 0;
            longVal(first, cursor++);
            uipNew += longVal(first, cursor++) == 1 ? 1 : 0;
            longVal(first, cursor++);
        }
        List<Object> second = stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) {
                operations.opsForHyperLogLog().size(todayUvKey);
                operations.opsForHyperLogLog().size(todayUipKey);
                return null;
            }
        });
        double uvCount = longVal(second, 0);
        double uipCount = longVal(second, 1);
        stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) {
                operations.opsForZSet().add(uvRankKey, url, uvCount);
                operations.expire(uvRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
                operations.opsForZSet().add(uipRankKey, url, uipCount);
                operations.expire(uipRankKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
                return null;
            }
        });
        return new RedisDelta(uvNew, uipNew);
    }

    private String resolveGid(String url, String gid) {
        if (StrUtil.isNotBlank(gid)) {
            return gid;
        }
        ShortLinkGoToDO gotoDO = shortLinkGoToMapper.selectOne(
                Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                        .eq(ShortLinkGoToDO::getFullShortUrl, url));
        return gotoDO == null ? gid : gotoDO.getGid();
    }

    private boolean claim(String eventId, List<String> claimedKeys) {
        if (StrUtil.isBlank(eventId)) {
            return true;
        }
        String key = IDEMPOTENT_PREFIX + eventId;
        Boolean absent = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", IDEMPOTENT_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(absent)) {
            return false;
        }
        claimedKeys.add(key);
        return true;
    }

    private void release(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        stringRedisTemplate.delete(keys);
    }

    private static long longVal(List<Object> results, int index) {
        if (results == null || index >= results.size() || results.get(index) == null) {
            return 0L;
        }
        Object value = results.get(index);
        if (value instanceof Boolean bool) {
            return bool ? 1L : 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private record RedisDelta(int uvNew, int uipNew) {
    }
}
