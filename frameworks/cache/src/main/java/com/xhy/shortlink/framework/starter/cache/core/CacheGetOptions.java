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

package com.xhy.shortlink.framework.starter.cache.core;

import lombok.Builder;
import lombok.Getter;
import org.redisson.api.RBloomFilter;

import java.util.concurrent.TimeUnit;

/**
 * 分布式缓存安全读取选项。
 */
@Getter
@Builder
public final class CacheGetOptions {

    private final long timeout;
    private final TimeUnit timeUnit;
    private final RBloomFilter<String> bloomFilter;
    private final CacheGetFilter<String> cacheGetFilter;
    private final CacheGetIfAbsent<String> cacheGetIfAbsent;

    /**
     * 构建仅包含超时配置的读取选项，{@code timeUnit} 为空时由实现回退到默认时间单位。
     */
    public static CacheGetOptions of(long timeout, TimeUnit timeUnit) {
        return builder().timeout(timeout).timeUnit(timeUnit).build();
    }
}
