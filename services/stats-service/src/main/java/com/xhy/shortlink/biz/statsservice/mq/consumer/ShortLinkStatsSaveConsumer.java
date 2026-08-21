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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private static final int CONSUME_THREADS = 16;
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
        consumer.setConsumeThreadMin(CONSUME_THREADS);
        consumer.setConsumeThreadMax(CONSUME_THREADS);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) ->
                handleMessages(messages));
    }

    ConsumeConcurrentlyStatus handleMessages(List<MessageExt> messages) {
        try {
            consumeBatch(parseMessages(messages));
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (RuntimeException ex) {
            log.warn("批量消费统计消息失败, size={}", messages.size(), ex);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    List<ShortLinkStatsRecordEvent> parseMessages(List<MessageExt> messages) {
        List<ShortLinkStatsRecordEvent> events = new ArrayList<>(messages.size());
        for (MessageExt message : messages) {
            ShortLinkStatsRecordEvent event = parseMessage(message);
            if (event != null) {
                if (StrUtil.isBlank(event.getEventId()) && StrUtil.isNotBlank(message.getMsgId())) {
                    event.setEventId(message.getMsgId());
                }
                events.add(event);
            }
        }
        return events;
    }

    public void consumeBatch(List<ShortLinkStatsRecordEvent> rawEvents) {
        long startNanos = System.nanoTime();
        List<Claim> claims = new ArrayList<>();
        Set<String> persistedUrls = new HashSet<>();
        try {
            List<ShortLinkStatsRecordEvent> events = new ArrayList<>();
            for (ShortLinkStatsRecordEvent event : rawEvents) {
                if (event == null || StrUtil.isBlank(event.getFullShortUrl())) {
                    continue;
                }
                if (event.getCurrentDate() == null) {
                    event.setCurrentDate(new Date());
                }
                if (claim(event, claims)) {
                    events.add(event);
                }
            }
            if (events.isEmpty()) {
                return;
            }
            persistClaimed(events, persistedUrls);
            statsMetrics.recordConsumeSuccess(events.size(), Duration.ofNanos(System.nanoTime() - startNanos));
        } catch (RuntimeException ex) {
            releaseUnpersisted(claims, persistedUrls);
            statsMetrics.recordConsumeFailure(Duration.ofNanos(System.nanoTime() - startNanos));
            throw ex;
        }
    }

    private void persistClaimed(List<ShortLinkStatsRecordEvent> events, Set<String> persistedUrls) {
        Map<String, List<ShortLinkStatsRecordEvent>> byUrl = new LinkedHashMap<>();
        for (ShortLinkStatsRecordEvent event : events) {
            byUrl.computeIfAbsent(event.getFullShortUrl(), key -> new ArrayList<>()).add(event);
        }
        List<String> urls = new ArrayList<>(byUrl.keySet());
        urls.sort(Comparator.naturalOrder());
        for (String url : urls) {
            persistUrl(url, byUrl.get(url), persistedUrls);
        }
    }

    private void persistUrl(String url, List<ShortLinkStatsRecordEvent> group, Set<String> persistedUrls) {
        UrlWrite write = new UrlWrite();
        RLock lock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, url)).readLock();
        lock.lock();
        try {
            write.gid = resolveGid(url, group);
            Map<String, List<ShortLinkStatsRecordEvent>> byDay = new LinkedHashMap<>();
            for (ShortLinkStatsRecordEvent event : group) {
                Date currentDate = event.getCurrentDate() == null ? new Date() : event.getCurrentDate();
                byDay.computeIfAbsent(DateUtil.formatDate(currentDate), key -> new ArrayList<>()).add(event);
            }
            for (List<ShortLinkStatsRecordEvent> dayGroup : byDay.values()) {
                addDayGroup(write, url, dayGroup);
            }
        } finally {
            lock.unlock();
        }
        persistedUrls.add(url);
        flush(url, write);
    }

    private void addDayGroup(UrlWrite write, String url, List<ShortLinkStatsRecordEvent> dayGroup) {
        List<EventRedisDelta> deltas;
        if (StrUtil.isBlank(write.gid)) {
            log.warn("统计消息缺少 gid, 跳过短链累计和排行, url={}", url);
            deltas = zeroDeltas(dayGroup.size());
        } else {
            deltas = applyRedis(url, write.gid, dayGroup);
        }
        for (int i = 0; i < dayGroup.size(); i++) {
            addEvent(write, dayGroup.get(i), deltas.get(i));
        }
    }

    private void addEvent(UrlWrite write, ShortLinkStatsRecordEvent event, EventRedisDelta delta) {
        write.pv += 1;
        write.totalUv += delta.totalUvNew();
        write.totalUip += delta.totalUipNew();
        Date currentDate = event.getCurrentDate() == null ? new Date() : event.getCurrentDate();
        String day = DateUtil.formatDate(currentDate);
        Date dayDate = DateUtil.parseDate(day);
        int hour = DateUtil.hour(currentDate, true);
        String url = event.getFullShortUrl();
        Optional<IpLocation> peeked = ipLocationService.peekWithoutHttp(event.getRemoteAddr());
        IpLocation location = peeked.orElse(IpLocation.unknown());
        LinkAccessLogsDO log = LinkAccessLogsDO.builder()
                .fullShortUrl(url)
                .ip(event.getRemoteAddr())
                .user(event.getUv())
                .os(event.getOs())
                .browser(event.getBrowser())
                .device(event.getDevice())
                .network(event.getNetwork())
                .locale(location.display())
                .build();
        write.logs.add(log);
        bumpAccess(write, url, dayDate, hour, delta);
        bump(write.osStats, url + "|" + day + "|" + event.getOs(),
                () -> LinkOsStatsDO.builder().fullShortUrl(url).date(dayDate).os(event.getOs()).cnt(1).build(),
                item -> item.setCnt(item.getCnt() + 1));
        bump(write.browserStats, url + "|" + day + "|" + event.getBrowser(),
                () -> LinkBrowserStatsDO.builder().fullShortUrl(url).date(dayDate).browser(event.getBrowser()).cnt(1).build(),
                item -> item.setCnt(item.getCnt() + 1));
        bump(write.deviceStats, url + "|" + day + "|" + event.getDevice(),
                () -> LinkDeviceStatsDO.builder().fullShortUrl(url).date(dayDate).device(event.getDevice()).cnt(1).build(),
                item -> item.setCnt(item.getCnt() + 1));
        bump(write.networkStats, url + "|" + day + "|" + event.getNetwork(),
                () -> LinkNetworkStatsDO.builder().fullShortUrl(url).date(dayDate).network(event.getNetwork()).cnt(1).build(),
                item -> item.setCnt(item.getCnt() + 1));
        if (peeked.isPresent()) {
            String localeKey = url + "|" + day + "|" + location.province() + "|" + location.city();
            bump(write.localeStats, localeKey,
                    () -> LinkLocaleStatsDO.builder()
                            .fullShortUrl(url).date(dayDate).cnt(1)
                            .country(LOCALE_COUNTRY_CN)
                            .province(location.province())
                            .city(location.city())
                            .adcode(location.adcode())
                            .build(),
                    item -> item.setCnt(item.getCnt() + 1));
        } else {
            write.localePendings.add(new LocalePending(event, currentDate, log));
        }
    }

    private void bumpAccess(UrlWrite write, String url, Date dayDate, int hour, EventRedisDelta delta) {
        int weekday = DateUtil.dayOfWeekEnum(dayDate).getIso8601Value();
        String accessKey = url + "|" + DateUtil.formatDate(dayDate) + "|" + hour;
        write.accessStats.compute(accessKey, (key, existing) -> {
            if (existing == null) {
                return LinkAccessStatsDO.builder()
                        .fullShortUrl(url).date(dayDate).hour(hour).weekday(weekday)
                        .pv(1).uv(delta.todayUvNew()).uip(delta.todayUipNew()).build();
            }
            existing.setPv(existing.getPv() + 1);
            existing.setUv(existing.getUv() + delta.todayUvNew());
            existing.setUip(existing.getUip() + delta.todayUipNew());
            return existing;
        });
    }

    private static <T> void bump(Map<String, T> stats, String key, Supplier<T> creator, Consumer<T> adder) {
        stats.compute(key, (ignored, existing) -> {
            if (existing == null) {
                return creator.get();
            }
            adder.accept(existing);
            return existing;
        });
    }

    private void flush(String url, UrlWrite write) {
        if (!write.logs.isEmpty()) {
            linkAccessLogsMapper.insertBatch(write.logs);
        }
        if (!write.accessStats.isEmpty()) {
            linkAccessStatsMapper.shortLinkStats(new ArrayList<>(write.accessStats.values()));
        }
        if (!write.osStats.isEmpty()) {
            linkOsStatsMapper.shortLinkOsStateBatch(new ArrayList<>(write.osStats.values()));
        }
        if (!write.browserStats.isEmpty()) {
            linkBrowserStatsMapper.shortLinkBrowserStateBatch(new ArrayList<>(write.browserStats.values()));
        }
        if (!write.deviceStats.isEmpty()) {
            linkDeviceStatsMapper.shortLinkDeviceStateBatch(new ArrayList<>(write.deviceStats.values()));
        }
        if (!write.networkStats.isEmpty()) {
            linkNetworkStatsMapper.shortLinkNetworkStateBatch(new ArrayList<>(write.networkStats.values()));
        }
        if (StrUtil.isNotBlank(write.gid)) {
            int affected = shortLinkMapper.incrementStats(write.gid, url, write.pv, write.totalUv, write.totalUip);
            if (affected == 0) {
                shortLinkColdMapper.incrementStats(write.gid, url, write.pv, write.totalUv, write.totalUip);
            }
        }
        if (!write.localeStats.isEmpty()) {
            linkLocaleStatsMapper.shortLinkLocaleStateBatch(new ArrayList<>(write.localeStats.values()));
        }
        for (LocalePending pending : write.localePendings) {
            accessLocaleEnricher.submit(pending.event().getFullShortUrl(), pending.accessDate(),
                    pending.event().getRemoteAddr(), pending.log().getId());
        }
    }

    private List<EventRedisDelta> applyRedis(String url, String gid, List<ShortLinkStatsRecordEvent> events) {
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
        List<EventRedisDelta> deltas = new ArrayList<>(events.size());
        int cursor = 2;
        for (int i = 0; i < events.size(); i++) {
            int todayUv = longVal(first, cursor++) == 1 ? 1 : 0;
            int totalUv = longVal(first, cursor++) == 1 ? 1 : 0;
            int todayUip = longVal(first, cursor++) == 1 ? 1 : 0;
            int totalUip = longVal(first, cursor++) == 1 ? 1 : 0;
            deltas.add(new EventRedisDelta(todayUv, todayUip, totalUv, totalUip));
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
        return deltas;
    }

    private String resolveGid(String url, List<ShortLinkStatsRecordEvent> events) {
        for (ShortLinkStatsRecordEvent event : events) {
            if (StrUtil.isNotBlank(event.getGid())) {
                return event.getGid();
            }
        }
        ShortLinkGoToDO gotoDO = shortLinkGoToMapper.selectOne(
                Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                        .eq(ShortLinkGoToDO::getFullShortUrl, url));
        return gotoDO == null ? null : gotoDO.getGid();
    }

    private ShortLinkStatsRecordEvent parseMessage(MessageExt message) {
        try {
            byte[] body = message.getBody();
            if (body == null || body.length == 0) {
                log.warn("统计消息 body 为空, msgId={}", message.getMsgId());
                return null;
            }
            ShortLinkStatsRecordEvent event = JSON.parseObject(new String(body, StandardCharsets.UTF_8),
                    ShortLinkStatsRecordEvent.class);
            if (event == null || StrUtil.isBlank(event.getFullShortUrl())) {
                log.warn("统计消息无法解析或缺少短链, msgId={}", message.getMsgId());
                return null;
            }
            return event;
        } catch (RuntimeException ex) {
            log.warn("统计消息 JSON 非法, msgId={}", message.getMsgId(), ex);
            return null;
        }
    }

    private boolean claim(ShortLinkStatsRecordEvent event, List<Claim> claims) {
        if (StrUtil.isBlank(event.getEventId())) {
            claims.add(new Claim(null, event.getFullShortUrl()));
            return true;
        }
        String key = IDEMPOTENT_PREFIX + event.getEventId();
        Boolean absent = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", IDEMPOTENT_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(absent)) {
            return false;
        }
        claims.add(new Claim(key, event.getFullShortUrl()));
        return true;
    }

    private void releaseUnpersisted(List<Claim> claims, Set<String> persistedUrls) {
        List<String> keys = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.key() != null && !persistedUrls.contains(claim.url())) {
                keys.add(claim.key());
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        stringRedisTemplate.delete(keys);
    }

    private static List<EventRedisDelta> zeroDeltas(int size) {
        List<EventRedisDelta> deltas = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            deltas.add(new EventRedisDelta(0, 0, 0, 0));
        }
        return deltas;
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
        if (value instanceof CharSequence text) {
            String normalized = text.toString().trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return 1L;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return 0L;
            }
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * 单条短链在一批内的聚合写缓冲。
     */
    private static final class UrlWrite {
        private String gid;
        private int pv;
        private int totalUv;
        private int totalUip;
        private final List<LinkAccessLogsDO> logs = new ArrayList<>();
        private final Map<String, LinkAccessStatsDO> accessStats = new HashMap<>();
        private final Map<String, LinkOsStatsDO> osStats = new HashMap<>();
        private final Map<String, LinkBrowserStatsDO> browserStats = new HashMap<>();
        private final Map<String, LinkDeviceStatsDO> deviceStats = new HashMap<>();
        private final Map<String, LinkNetworkStatsDO> networkStats = new HashMap<>();
        private final Map<String, LinkLocaleStatsDO> localeStats = new HashMap<>();
        private final List<LocalePending> localePendings = new ArrayList<>();
    }

    private record LocalePending(ShortLinkStatsRecordEvent event, Date accessDate, LinkAccessLogsDO log) {
    }

    private record Claim(String key, String url) {
    }

    private record EventRedisDelta(int todayUvNew, int todayUipNew, int totalUvNew, int totalUipNew) {
    }
}
