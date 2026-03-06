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
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToDO;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;

import static com.xhy.shortlink.biz.projectservice.common.constant.ShortLinkConstant.PAGE_NOT_FOUND;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShortLinkRedirectServiceImplTest {

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
    private RedissonClient redissonClient;
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
    void redirect_cacheHit_redirectsSuccessfully() throws Exception {
        String shortUri = "abc123";
        String cacheKey = "short-link:goto:test.cn/abc123:";
        long futureTs = System.currentTimeMillis() + 86400000L;
        String cacheValue = futureTs + "|https://www.example.com|g1";

        when(shortLinkCache.getIfPresent(cacheKey)).thenReturn(cacheValue);
        when(stringRedisTemplate.expire(eq(cacheKey), anyLong(), any())).thenReturn(true);

        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("uv", "existing-uv")});
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        redirectService.redirect(shortUri, request, response);

        verify(response).sendRedirect("https://www.example.com");
        verify(statsProducer).sendMessage(any());
        verify(shortLinkMetrics).recordRedirectSuccess(any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redirect_bloomFilterNotContain_redirects404() throws Exception {
        String shortUri = "notexist";

        when(shortLinkCache.getIfPresent(anyString())).thenReturn(null);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("goto:test.cn/notexist"))).thenReturn(null);
        when(cacheHelper.bloomFilterContains("test.cn/notexist")).thenReturn(false);

        redirectService.redirect(shortUri, request, response);

        verify(response).sendRedirect(PAGE_NOT_FOUND);
        verify(shortLinkMetrics).recordRedirectFailure(any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redirect_bloomFilterContains_nullCacheHit_redirects404() throws Exception {
        String shortUri = "nullcache";

        when(shortLinkCache.getIfPresent(anyString())).thenReturn(null);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("goto:test.cn/nullcache"))).thenReturn(null);
        when(cacheHelper.bloomFilterContains("test.cn/nullcache")).thenReturn(true);
        when(valueOps.get(contains("goto:is-null:test.cn/nullcache"))).thenReturn("-");

        redirectService.redirect(shortUri, request, response);

        verify(response).sendRedirect(PAGE_NOT_FOUND);
        verify(shortLinkMetrics).recordRedirectFailure(any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redirect_cacheMiss_dbHit_rebuildsCache() throws Exception {
        String shortUri = "dbhit";
        String fullShortUrl = "test.cn/dbhit";

        when(shortLinkCache.getIfPresent(anyString())).thenReturn(null);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("goto:test.cn/dbhit"))).thenReturn(null);
        when(valueOps.get(contains("goto:is-null:test.cn/dbhit"))).thenReturn(null);
        when(cacheHelper.bloomFilterContains(fullShortUrl)).thenReturn(true);

        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        ShortLinkGoToDO goToDO = ShortLinkGoToDO.builder().gid("g1").fullShortUrl(fullShortUrl).build();
        when(shortLinkGoToMapper.selectOne(any())).thenReturn(goToDO);

        ShortLinkDO linkDO = ShortLinkDO.builder()
                .fullShortUrl(fullShortUrl)
                .originUrl("https://www.target.com")
                .gid("g1")
                .validDate(null)
                .enableStatus(0)
                .build();
        when(shortLinkMapper.selectOne(any())).thenReturn(linkDO);

        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(request.getCookies()).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        redirectService.redirect(shortUri, request, response);

        verify(cacheHelper).rebuildCache(eq(fullShortUrl), eq("https://www.target.com"), eq("g1"), isNull());
        verify(response).sendRedirect("https://www.target.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redirect_cacheMiss_coldDbHit_triggersRehot() throws Exception {
        String shortUri = "coldhit";
        String fullShortUrl = "test.cn/coldhit";

        when(shortLinkCache.getIfPresent(anyString())).thenReturn(null);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("goto:test.cn/coldhit"))).thenReturn(null);
        when(valueOps.get(contains("goto:is-null:test.cn/coldhit"))).thenReturn(null);
        when(cacheHelper.bloomFilterContains(fullShortUrl)).thenReturn(true);

        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        when(shortLinkGoToMapper.selectOne(any())).thenReturn(null);

        ShortLinkGoToColdDO coldGoTo = ShortLinkGoToColdDO.builder().gid("g1").fullShortUrl(fullShortUrl).build();
        when(shortLinkGoToColdMapper.selectOne(any())).thenReturn(coldGoTo);

        ShortLinkColdDO coldDO = new ShortLinkColdDO();
        coldDO.setFullShortUrl(fullShortUrl);
        coldDO.setOriginUrl("https://www.cold-target.com");
        coldDO.setGid("g1");
        coldDO.setValidDate(null);
        coldDO.setEnableStatus(0);
        when(shortLinkColdMapper.selectOne(any())).thenReturn(coldDO);

        ColdDataProperties.Rehot rehotConfig = new ColdDataProperties.Rehot();
        rehotConfig.setThreshold(1000);
        when(coldDataProperties.getRehot()).thenReturn(rehotConfig);
        when(valueOps.increment(anyString())).thenReturn(1L);

        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(request.getCookies()).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        redirectService.redirect(shortUri, request, response);

        verify(cacheHelper).rebuildCache(eq(fullShortUrl), eq("https://www.cold-target.com"), eq("g1"), isNull());
        verify(response).sendRedirect("https://www.cold-target.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redirect_cacheMiss_dbMiss_writesNullCache() throws Exception {
        String shortUri = "dbmiss";
        String fullShortUrl = "test.cn/dbmiss";

        when(shortLinkCache.getIfPresent(anyString())).thenReturn(null);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("goto:test.cn/dbmiss"))).thenReturn(null);
        when(valueOps.get(contains("goto:is-null:test.cn/dbmiss"))).thenReturn(null);
        when(cacheHelper.bloomFilterContains(fullShortUrl)).thenReturn(true);

        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        when(shortLinkGoToMapper.selectOne(any())).thenReturn(null);
        when(shortLinkGoToColdMapper.selectOne(any())).thenReturn(null);

        redirectService.redirect(shortUri, request, response);

        verify(valueOps).set(contains("goto:is-null:test.cn/dbmiss"), eq("-"), anyLong(), any());
        verify(response).sendRedirect(PAGE_NOT_FOUND);
        verify(shortLinkMetrics).recordRedirectFailure(any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redirect_expiredCache_redirects404ViaLock() throws Exception {
        String shortUri = "expired";
        String fullShortUrl = "test.cn/expired";
        long pastTs = System.currentTimeMillis() - 86400000L;
        String cacheValue = pastTs + "|https://www.example.com|g1";

        when(shortLinkCache.getIfPresent(anyString())).thenReturn(cacheValue);
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("goto:is-null:test.cn/expired"))).thenReturn(null);
        when(cacheHelper.bloomFilterContains(fullShortUrl)).thenReturn(true);

        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        when(shortLinkGoToMapper.selectOne(any())).thenReturn(null);
        when(shortLinkGoToColdMapper.selectOne(any())).thenReturn(null);

        redirectService.redirect(shortUri, request, response);

        verify(valueOps).set(contains("goto:is-null:"), eq("-"), anyLong(), any());
        verify(response).sendRedirect(PAGE_NOT_FOUND);
    }
}
