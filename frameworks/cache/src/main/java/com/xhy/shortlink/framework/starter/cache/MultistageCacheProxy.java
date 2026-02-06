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

package com.xhy.shortlink.framework.starter.cache;

import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xhy.shortlink.framework.starter.cache.config.MultistageCacheProperties;
import com.xhy.shortlink.framework.starter.cache.config.RedisDistributedProperties;
import com.xhy.shortlink.framework.starter.cache.core.CacheLoader;
import com.xhy.shortlink.framework.starter.cache.toolkit.CacheUtil;
import com.xhy.shortlink.framework.starter.cache.toolkit.FastJson2Util;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 多级缓存代理实现（Caffeine L1 + Redis L2）
 * <p>
 * 继承 {@link StringRedisTemplateProxy} 的全部分布式缓存能力，
 * 额外提供 L1 本地缓存操作和 L1 → L2 → CacheLoader 的多级穿透查询
 */
public class MultistageCacheProxy extends StringRedisTemplateProxy implements MultistageCache {

    private final Cache<String, String> localCache;
    private final RedisDistributedProperties redisProperties;

    public MultistageCacheProxy(StringRedisTemplate stringRedisTemplate,
                                RedisDistributedProperties redisProperties,
                                RedissonClient redissonClient,
                                MultistageCacheProperties multistageCacheProperties) {
        super(stringRedisTemplate, redisProperties, redissonClient);
        this.redisProperties = redisProperties;
        this.localCache = Caffeine.newBuilder()
                .initialCapacity(multistageCacheProperties.getInitialCapacity())
                .maximumSize(multistageCacheProperties.getMaximumSize())
                .expireAfterWrite(multistageCacheProperties.getExpireAfterWrite(), TimeUnit.MINUTES)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getLocal(String key, Class<T> clazz) {
        String value = localCache.getIfPresent(key);
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(clazz)) {
            return (T) value;
        }
        return JSON.parseObject(value, FastJson2Util.buildType(clazz));
    }

    @Override
    public void putLocal(String key, Object value) {
        String actual = value instanceof String ? (String) value : JSON.toJSONString(value);
        localCache.put(key, actual);
    }

    @Override
    public void invalidateLocal(String key) {
        localCache.invalidate(key);
    }

    @Override
    public <T> T multiGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout) {
        return multiGet(key, clazz, cacheLoader, timeout, redisProperties.getValueTimeUnit());
    }

    @Override
    public <T> T multiGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit) {
        // L1: 本地缓存命中直接返回
        T result = getLocal(key, clazz);
        if (!CacheUtil.isNullOrBlank(result)) {
            return result;
        }
        // L2: Redis 缓存查询
        result = get(key, clazz);
        if (!CacheUtil.isNullOrBlank(result)) {
            // 回填 L1
            putLocal(key, result);
            return result;
        }
        // L3: CacheLoader 回源
        result = cacheLoader.load();
        if (!CacheUtil.isNullOrBlank(result)) {
            // 写入 L1 + L2
            put(key, result, timeout, timeUnit);
            putLocal(key, result);
        }
        return result;
    }
}
