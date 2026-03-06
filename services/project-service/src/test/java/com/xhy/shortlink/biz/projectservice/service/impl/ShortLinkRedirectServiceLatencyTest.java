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

package com.xhy.shortlink.biz.projectservice.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.xhy.shortlink.biz.projectservice.config.ColdDataProperties;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.helper.ShortLinkCacheHelper;
import com.xhy.shortlink.biz.projectservice.metrics.ShortLinkMetrics;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkStatsProducer;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkRedirectServiceLatencyTest {

    @InjectMocks
    private ShortLinkRedirectServiceImpl redirectService;

    @Mock
    private ShortLinkGoToMapper shortLinkGoToMapper;
    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private ShortLinkColdMapper shortLinkColdMapper;
    @Mock
    private ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private org.redisson.api.RedissonClient redissonClient;
    @Mock
    private ShortLinkStatsProducer statsProducer;
    @Mock
    private ShortLinkCacheHelper cacheHelper;
    @Mock
    @SuppressWarnings("rawtypes")
    private Cache shortLinkCache;
    @Mock
    private ColdDataProperties coldDataProperties;
    @Mock
    private ShortLinkMetrics shortLinkMetrics;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redirectService, "defaultDomain", "test.cn");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redirect_cacheHit_successLatencyIncludesPreRedirectLookupTime() throws Exception {
        String shortUri = "latency-hit";
        String cacheKey = "short-link:goto:test.cn/latency-hit:";
        long futureTs = System.currentTimeMillis() + 86400000L;
        String cacheValue = futureTs + "|https://www.example.com|g1";

        when(shortLinkCache.getIfPresent(cacheKey)).thenAnswer(invocation -> {
            Thread.sleep(40);
            return cacheValue;
        });
        when(stringRedisTemplate.expire(eq(cacheKey), anyLong(), any())).thenReturn(true);

        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("uv", "existing-uv")});
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        redirectService.redirect(shortUri, request, response);

        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(shortLinkMetrics).recordRedirectSuccess(durationCaptor.capture());
        assertTrue(durationCaptor.getValue().toMillis() >= 30,
                "redirect latency should include cache lookup time before executeRedirect");
    }
}
