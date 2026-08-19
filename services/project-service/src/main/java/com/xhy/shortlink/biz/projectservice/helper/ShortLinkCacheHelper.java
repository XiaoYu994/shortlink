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

package com.xhy.shortlink.biz.projectservice.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkCacheService;
import com.xhy.shortlink.biz.projectservice.toolkit.LinkUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;

/**
 * 短链接缓存服务实现
 * <p>
 * 封装 Caffeine 本地缓存 + Redis 分布式缓存 + 布隆过滤器的统一操作，
 * 供 CoreService、RedirectService、MQ Consumer 共用。
 *
 * @author XiaoYu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkCacheHelper implements ShortLinkCacheService {

    /** 永久有效链接的缓存时间戳标记 */
    private static final long PERMANENT_VALID_TIMESTAMP = -1;
    /** 新缓存格式版本，避免原始 URL 中的分隔符破坏解析。 */
    private static final String CACHE_FORMAT_VERSION = "2";

    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<String, String> shortLinkCache;
    private final RBloomFilter<String> shortlinkUriCreateCachePenetrationBloomFilter;

    @Value("${short-link.cache.ttl-jitter-ratio:0.1}")
    private double ttlJitterRatio = 0.1;

    /**
     * 缓存预热：写入 Redis + 删除空值缓存 + 加入布隆过滤器
     *
     * @param fullShortUrl 完整短链接
     * @param originUrl    原始链接
     * @param gid          分组标识
     * @param validDate    有效期（null 表示永久）
     */
    public void warmUp(String fullShortUrl, String originUrl, String gid, Date validDate) {
        warmUp(fullShortUrl, originUrl, gid, validDate, false);
    }

    /**
     * 缓存预热并记录数据来源，冷库回源后的缓存命中也需要参与回温计数。
     */
    public void warmUp(String fullShortUrl, String originUrl, String gid, Date validDate, boolean fromCold) {
        long validTimeStamp = (validDate != null) ? validDate.getTime() : PERMANENT_VALID_TIMESTAMP;
        String cacheValue = buildCacheValue(validTimeStamp, originUrl, gid, fromCold);
        long initialTTL = applyTtlJitter(LinkUtil.getLinkCacheValidTime(validDate));
        stringRedisTemplate.opsForValue().set(
                String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                cacheValue, initialTTL, TimeUnit.MILLISECONDS);
        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        shortlinkUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
    }

    /**
     * 重建多级缓存：写入 L1 Caffeine + L2 Redis（跳转回源时调用）
     */
    public void rebuildCache(String fullShortUrl, String originUrl, String gid, Date validDate) {
        rebuildCache(fullShortUrl, originUrl, gid, validDate, false);
    }

    /**
     * 重建多级缓存并保留冷库来源标记。
     */
    public void rebuildCache(String fullShortUrl, String originUrl, String gid, Date validDate, boolean fromCold) {
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        long validTimeStamp = (validDate != null) ? validDate.getTime() : PERMANENT_VALID_TIMESTAMP;
        String cacheValue = buildCacheValue(validTimeStamp, originUrl, gid, fromCold);
        long initialTTL = applyTtlJitter(LinkUtil.getLinkCacheValidTime(validDate));
        stringRedisTemplate.opsForValue().set(key, cacheValue, initialTTL, TimeUnit.MILLISECONDS);
        shortLinkCache.put(key, cacheValue);
    }

    /**
     * 清除本地 Caffeine 缓存（由 MQ 广播消费者调用）
     */
    public void evictLocalCache(String fullShortUrl) {
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
        shortLinkCache.invalidate(key);
        shortLinkCache.invalidate(keyIsNull);
    }

    /**
     * 添加到布隆过滤器
     */
    public void addToBloomFilter(String fullShortUrl) {
        shortlinkUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
    }

    /**
     * 检查布隆过滤器是否包含
     */
    public boolean bloomFilterContains(String fullShortUrl) {
        return shortlinkUriCreateCachePenetrationBloomFilter.contains(fullShortUrl);
    }

    /**
     * 给 TTL 加随机抖动，避免同一批短链同时过期造成缓存雪崩
     */
    private long applyTtlJitter(long ttlMillis) {
        if (ttlMillis <= 1 || ttlJitterRatio <= 0) {
            return ttlMillis;
        }
        long maxJitter = Math.max(1L, (long) (ttlMillis * ttlJitterRatio));
        return ttlMillis + ThreadLocalRandom.current().nextLong(maxJitter);
    }

    private String buildCacheValue(long validTimeStamp, String originUrl, String gid, boolean fromCold) {
        return String.format("%s|%d|%s|%s|%d",
                CACHE_FORMAT_VERSION,
                validTimeStamp,
                encode(originUrl),
                encode(gid),
                fromCold ? 1 : 0);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
