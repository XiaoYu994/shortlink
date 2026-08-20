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

import com.xhy.shortlink.framework.starter.cache.config.RedisDistributedProperties;
import com.xhy.shortlink.framework.starter.cache.core.CacheGetOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StringRedisTemplateProxyTest {

    private static final String CACHE_KEY = "cache:key";
    private static final long CACHE_TIMEOUT = 30L;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisDistributedProperties redisProperties;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RLock lock;

    @Mock
    private RBloomFilter<String> bloomFilter;

    private StringRedisTemplateProxy cache;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new StringRedisTemplateProxy(stringRedisTemplate, redisProperties, redissonClient);
    }

    @Test
    void safeGet_returnsCachedValueWithoutLoading() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("cached");

        String result = cache.safeGet(CACHE_KEY, String.class, () -> "loaded",
                CacheGetOptions.of(CACHE_TIMEOUT, TimeUnit.SECONDS));

        assertEquals("cached", result);
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void safeGet_bloomFilterMiss_skipsDatabaseLoader() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(bloomFilter.contains(CACHE_KEY)).thenReturn(false);

        String result = cache.safeGet(CACHE_KEY, String.class, () -> "loaded",
                CacheGetOptions.builder()
                        .timeout(CACHE_TIMEOUT)
                        .timeUnit(TimeUnit.SECONDS)
                        .bloomFilter(bloomFilter)
                        .build());

        assertNull(result);
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void safeGet_cacheGetFilterMatch_skipsLoaderAndLock() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        AtomicBoolean loaded = new AtomicBoolean();

        String result = cache.safeGet(CACHE_KEY, String.class, () -> {
            loaded.set(true);
            return "loaded";
        }, CacheGetOptions.builder()
                .timeout(CACHE_TIMEOUT)
                .timeUnit(TimeUnit.SECONDS)
                .cacheGetFilter(key -> true)
                .build());

        assertNull(result);
        assertFalse(loaded.get());
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void safeGet_loadsAndInvokesMissingCallbackWhenSourceIsEmpty() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        AtomicReference<String> absentKey = new AtomicReference<>();

        String result = cache.safeGet(CACHE_KEY, String.class, () -> null,
                CacheGetOptions.builder()
                        .timeout(CACHE_TIMEOUT)
                        .timeUnit(TimeUnit.SECONDS)
                        .cacheGetIfAbsent(absentKey::set)
                        .build());

        assertNull(result);
        assertEquals(CACHE_KEY, absentKey.get());
        verify(lock).unlock();
    }

    @Test
    void safeGet_loadsValueAndUpdatesBloomFilter() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(bloomFilter.contains(CACHE_KEY)).thenReturn(true);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        String result = cache.safeGet(CACHE_KEY, String.class, () -> "loaded",
                CacheGetOptions.builder()
                        .timeout(CACHE_TIMEOUT)
                        .timeUnit(TimeUnit.SECONDS)
                        .bloomFilter(bloomFilter)
                        .build());

        assertEquals("loaded", result);
        verify(valueOperations).set(CACHE_KEY, "loaded", CACHE_TIMEOUT, TimeUnit.SECONDS);
        verify(bloomFilter).add(CACHE_KEY);
        verify(lock).unlock();
    }

    @Test
    void safeGet_timeoutOnlyOverload_usesConfiguredTimeUnit() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(redisProperties.getValueTimeUnit()).thenReturn(TimeUnit.MILLISECONDS);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        String result = cache.safeGet(CACHE_KEY, String.class, () -> "loaded", CACHE_TIMEOUT);

        assertEquals("loaded", result);
        verify(valueOperations).set(CACHE_KEY, "loaded", CACHE_TIMEOUT, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void safeGet_missingTimeUnit_fallsBackToConfiguredTimeUnit() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(redisProperties.getValueTimeUnit()).thenReturn(TimeUnit.MILLISECONDS);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        String result = cache.safeGet(CACHE_KEY, String.class, () -> "loaded",
                CacheGetOptions.builder().timeout(CACHE_TIMEOUT).build());

        assertEquals("loaded", result);
        verify(valueOperations).set(CACHE_KEY, "loaded", CACHE_TIMEOUT, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void safeGet_nullOptions_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                cache.safeGet(CACHE_KEY, String.class, () -> "loaded", (CacheGetOptions) null));
    }

    @Test
    void safeGet_nonPositiveTimeout_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                cache.safeGet(CACHE_KEY, String.class, () -> "loaded",
                        CacheGetOptions.builder().timeout(0).timeUnit(TimeUnit.SECONDS).build()));
    }
}
