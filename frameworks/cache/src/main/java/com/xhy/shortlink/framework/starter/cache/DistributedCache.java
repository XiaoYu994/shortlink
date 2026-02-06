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

import com.xhy.shortlink.framework.starter.cache.core.CacheGetFilter;
import com.xhy.shortlink.framework.starter.cache.core.CacheGetIfAbsent;
import com.xhy.shortlink.framework.starter.cache.core.CacheLoader;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.redisson.api.RBloomFilter;

import java.util.concurrent.TimeUnit;

/**
 * 分布式缓存接口，扩展 {@link Cache} 提供安全读写、布隆过滤器防穿透等能力
 */
public interface DistributedCache extends Cache {

    /**
     * 获取缓存，未命中时通过 {@link CacheLoader} 回源加载并写入缓存（使用默认时间单位）
     */
    <T> T get(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout);

    /**
     * 获取缓存，未命中时通过 {@link CacheLoader} 回源加载并写入缓存
     */
    <T> T get(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit);

    /**
     * 安全获取缓存（分布式锁防击穿 + 双重检查），使用默认时间单位
     */
    <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout);

    /**
     * 安全获取缓存（分布式锁防击穿 + 双重检查）
     */
    <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit);

    /**
     * 安全获取缓存（布隆过滤器防穿透 + 分布式锁防击穿），使用默认时间单位
     */
    <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, RBloomFilter<String> bloomFilter);

    /**
     * 安全获取缓存（布隆过滤器防穿透 + 分布式锁防击穿）
     */
    <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter);

    /**
     * 安全获取缓存（布隆过滤器防穿透 + {@link CacheGetFilter} 补偿删除 + 分布式锁防击穿），使用默认时间单位
     */
    <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout,
                  RBloomFilter<String> bloomFilter, CacheGetFilter<String> cacheCheckFilter);

    /**
     * 安全获取缓存（布隆过滤器防穿透 + {@link CacheGetFilter} 补偿删除 + 分布式锁防击穿）
     */
    <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit,
                  RBloomFilter<String> bloomFilter, CacheGetFilter<String> cacheCheckFilter);

    /**
     * 安全获取缓存（布隆过滤器防穿透 + {@link CacheGetFilter} 补偿删除 + 分布式锁防击穿 + 缺失回调），使用默认时间单位
     */
    <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout,
                  RBloomFilter<String> bloomFilter, CacheGetFilter<String> cacheCheckFilter, CacheGetIfAbsent<String> cacheGetIfAbsent);

    /**
     * 安全获取缓存（布隆过滤器防穿透 + {@link CacheGetFilter} 补偿删除 + 分布式锁防击穿 + 缺失回调）
     */
    <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit,
                  RBloomFilter<String> bloomFilter, CacheGetFilter<String> cacheCheckFilter, CacheGetIfAbsent<String> cacheGetIfAbsent);

    /**
     * 放入缓存，自定义超时时间（使用默认时间单位）
     */
    void put(@NotBlank String key, Object value, long timeout);

    /**
     * 放入缓存，自定义超时时间和时间单位
     */
    void put(@NotBlank String key, Object value, long timeout, TimeUnit timeUnit);

    /**
     * 安全放入缓存并将 key 加入布隆过滤器（使用默认时间单位）
     */
    void safePut(@NotBlank String key, Object value, long timeout, RBloomFilter<String> bloomFilter);

    /**
     * 安全放入缓存并将 key 加入布隆过滤器
     */
    void safePut(@NotBlank String key, Object value, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter);

    /**
     * 统计指定 key 的存在数量
     */
    Long countExistingKeys(@NotNull String... keys);
}
