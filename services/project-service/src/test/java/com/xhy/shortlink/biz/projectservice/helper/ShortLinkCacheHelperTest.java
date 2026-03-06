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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortLinkCacheHelperTest {

    @InjectMocks
    private ShortLinkCacheHelper cacheHelper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private Cache<String, String> shortLinkCache;
    @Mock
    private RBloomFilter<String> shortlinkUriCreateCachePenetrationBloomFilter;

    @Test
    @SuppressWarnings("unchecked")
    void warmUp_permanent_setsRedisAndBloomFilter() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        cacheHelper.warmUp("test.cn/abc", "https://example.com", "g1", null);

        verify(valueOps).set(
                eq("short-link:goto:test.cn/abc:"),
                contains("example.com"),
                anyLong(),
                any());
        verify(stringRedisTemplate).delete("short-link:goto:is-null:test.cn/abc:");
        verify(shortlinkUriCreateCachePenetrationBloomFilter).add("test.cn/abc");
    }

    @Test
    @SuppressWarnings("unchecked")
    void warmUp_withValidDate_includesTimestamp() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        Date future = new Date(System.currentTimeMillis() + 86400000L);
        cacheHelper.warmUp("test.cn/xyz", "https://example.com", "g1", future);

        verify(valueOps).set(
                eq("short-link:goto:test.cn/xyz:"),
                argThat(value -> !value.startsWith("-1|")),
                anyLong(),
                any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuildCache_writesBothLevels() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        cacheHelper.rebuildCache("test.cn/abc", "https://example.com", "g1", null);

        verify(valueOps).set(
                eq("short-link:goto:test.cn/abc:"),
                contains("example.com"),
                anyLong(),
                any());
        verify(shortLinkCache).put(eq("short-link:goto:test.cn/abc:"), contains("example.com"));
    }

    @Test
    void evictLocalCache_invalidatesBothKeys() {
        cacheHelper.evictLocalCache("test.cn/abc");

        verify(shortLinkCache).invalidate("short-link:goto:test.cn/abc:");
        verify(shortLinkCache).invalidate("short-link:goto:is-null:test.cn/abc:");
    }

    @Test
    void addToBloomFilter_delegatesToFilter() {
        cacheHelper.addToBloomFilter("test.cn/abc");

        verify(shortlinkUriCreateCachePenetrationBloomFilter).add("test.cn/abc");
    }

    @Test
    void bloomFilterContains_returnsTrueWhenPresent() {
        when(shortlinkUriCreateCachePenetrationBloomFilter.contains("test.cn/abc")).thenReturn(true);

        assertTrue(cacheHelper.bloomFilterContains("test.cn/abc"));
    }

    @Test
    void bloomFilterContains_returnsFalseWhenAbsent() {
        when(shortlinkUriCreateCachePenetrationBloomFilter.contains("test.cn/notexist")).thenReturn(false);

        assertFalse(cacheHelper.bloomFilterContains("test.cn/notexist"));
    }
}
