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

import com.xhy.shortlink.framework.starter.cache.core.CacheLoader;

import java.util.concurrent.TimeUnit;

/**
 * 多级缓存接口，扩展 {@link DistributedCache}
 * <p>
 * 提供 L1 本地缓存操作和多级缓存穿透查询能力（L1 → L2 → CacheLoader 回源）
 */
public interface MultistageCache extends DistributedCache {

    /**
     * 从 L1 本地缓存获取值并反序列化为指定类型
     */
    <T> T getLocal(String key, Class<T> clazz);

    /**
     * 写入 L1 本地缓存
     */
    void putLocal(String key, Object value);

    /**
     * 失效 L1 本地缓存中指定 key
     */
    void invalidateLocal(String key);

    /**
     * 多级缓存查询：L1 本地缓存 → L2 Redis → CacheLoader 回源（使用默认时间单位）
     */
    <T> T multiGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout);

    /**
     * 多级缓存查询：L1 本地缓存 → L2 Redis → CacheLoader 回源
     */
    <T> T multiGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit);
}
