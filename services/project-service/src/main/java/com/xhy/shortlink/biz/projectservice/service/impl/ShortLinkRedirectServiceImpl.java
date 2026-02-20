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

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.xhy.shortlink.biz.projectservice.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.helper.ShortLinkCacheHelper;
import com.xhy.shortlink.biz.projectservice.mq.event.ShortLinkStatsRecordEvent;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkStatsProducer;
import com.xhy.shortlink.biz.projectservice.toolkit.LinkUtil;
import cn.hutool.core.text.CharSequenceUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.*;
import static com.xhy.shortlink.biz.projectservice.common.constant.ShortLinkConstant.DEFAULT_COOKIE_VALID_TIME;
import static com.xhy.shortlink.biz.projectservice.common.constant.ShortLinkConstant.PAGE_NOT_FOUND;

/**
 * 短链接跳转重定向服务实现
 *
 * @author XiaoYu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkRedirectServiceImpl {

    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ShortLinkStatsProducer statsProducer;
    private final ShortLinkCacheHelper cacheHelper;
    private final Cache<String, String> shortLinkCache;

    @Value("${short-link.domain.default}")
    private String defaultDomain;

    @SneakyThrows
    public void redirect(String shortUri, ServletRequest request, ServletResponse response) {
        String fullShortUrl = defaultDomain + "/" + shortUri;
        ShortLinkCacheObj cacheObj = getFromCache(fullShortUrl);
        if (cacheObj != null) {
            executeRedirect(fullShortUrl, cacheObj, request, response);
            return;
        }
        if (isPossiblePenetration(fullShortUrl)) {
            ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
            return;
        }
        processWithLock(fullShortUrl, request, response);
    }

    private ShortLinkCacheObj getFromCache(String fullShortUrl) {
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        String composite = shortLinkCache.getIfPresent(key);
        if (CharSequenceUtil.isBlank(composite)) {
            composite = stringRedisTemplate.opsForValue().get(key);
            if (CharSequenceUtil.isBlank(composite)) {
                return null;
            }
            shortLinkCache.put(key, composite);
        }
        ShortLinkCacheObj cacheObj = parseCache(composite, key);
        if (cacheObj == null) {
            shortLinkCache.invalidate(key);
            stringRedisTemplate.delete(key);
        }
        return cacheObj;
    }

    private boolean isPossiblePenetration(String fullShortUrl) {
        if (!cacheHelper.bloomFilterContains(fullShortUrl)) {
            return true;
        }
        String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
        return CharSequenceUtil.isNotBlank(stringRedisTemplate.opsForValue().get(keyIsNull));
    }

    private void processWithLock(String fullShortUrl, ServletRequest request, ServletResponse response) throws IOException {
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            ShortLinkCacheObj cacheObj = getFromCache(fullShortUrl);
            if (cacheObj != null) {
                executeRedirect(fullShortUrl, cacheObj, request, response);
                return;
            }
            if (isPossiblePenetration(fullShortUrl)) {
                ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
                return;
            }
            ShortLinkCacheObj dbObj = loadFromDb(fullShortUrl);
            if (dbObj != null) {
                cacheHelper.rebuildCache(fullShortUrl, dbObj.getOriginUrl(), dbObj.getGid(), dbObj.getValidDate());
                executeRedirect(fullShortUrl, dbObj, request, response);
            } else {
                String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
                stringRedisTemplate.opsForValue().set(keyIsNull, "-", DEFAULT_CACHE_VALID_TIME_FOR_GOTO, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
            }
        } finally {
            lock.unlock();
        }
    }

    private ShortLinkCacheObj loadFromDb(String fullShortUrl) {
        ShortLinkGoToDO goToDO = shortLinkGoToMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                .eq(ShortLinkGoToDO::getFullShortUrl, fullShortUrl));
        if (goToDO != null) {
            ShortLinkDO linkDO = shortLinkMapper.selectOne(Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getGid, goToDO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getCode()));
            if (linkDO != null && isNotExpired(linkDO.getValidDate())) {
                return new ShortLinkCacheObj(linkDO.getOriginUrl(), linkDO.getGid(), linkDO.getValidDate());
            }
            return null;
        }
        ShortLinkGoToColdDO coldGoToDO = shortLinkGoToColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
        if (coldGoToDO != null) {
            ShortLinkColdDO coldDO = shortLinkColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkColdDO::getGid, coldGoToDO.getGid())
                    .eq(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getCode()));
            if (coldDO != null && isNotExpired(coldDO.getValidDate())) {
                return new ShortLinkCacheObj(coldDO.getOriginUrl(), coldDO.getGid(), coldDO.getValidDate());
            }
        }
        return null;
    }

    @SneakyThrows
    private void executeRedirect(String fullShortUrl, ShortLinkCacheObj cacheObj, ServletRequest request, ServletResponse response) {
        statsProducer.sendMessage(buildLinkStatsRecordDTO(fullShortUrl, cacheObj.getGid(), request, response));
        ((HttpServletResponse) response).sendRedirect(cacheObj.getOriginUrl());
    }

    private boolean isNotExpired(Date validDate) {
        return validDate == null || validDate.after(new Date());
    }

    private ShortLinkCacheObj parseCache(String composite, String key) {
        String[] split = composite.split("\\|");
        if (split.length < 3) {
            return null;
        }
        long validTime = Long.parseLong(split[0]);
        String originalLink = split[1];
        String gid = split[2];
        if (validTime != -1 && System.currentTimeMillis() > validTime) {
            stringRedisTemplate.delete(key);
            return null;
        }
        long expireTime;
        if (validTime == -1) {
            expireTime = TimeUnit.DAYS.toMillis(1);
        } else {
            long remainingTime = validTime - System.currentTimeMillis();
            expireTime = Math.min(remainingTime, TimeUnit.DAYS.toMillis(1));
        }
        if (expireTime > 0) {
            stringRedisTemplate.expire(key, expireTime, TimeUnit.MILLISECONDS);
        }
        Date validDate = validTime == -1 ? null : new Date(validTime);
        return new ShortLinkCacheObj(originalLink, gid, validDate);
    }

    private ShortLinkStatsRecordEvent buildLinkStatsRecordDTO(String fullShortUrl, String gid, ServletRequest request, ServletResponse response) {
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        AtomicReference<String> uv = new AtomicReference<>();
        Runnable generateNewCookieTask = () -> {
            uv.set(UUID.fastUUID().toString());
            Cookie cookie = new Cookie("uv", uv.get());
            cookie.setMaxAge(DEFAULT_COOKIE_VALID_TIME);
            cookie.setPath("/");
            ((HttpServletResponse) response).addCookie(cookie);
            uvFirstFlag.set(true);
            stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, uv.get());
        };
        if (ArrayUtil.isNotEmpty(cookies)) {
            Arrays.stream(cookies)
                    .filter(cookie -> "uv".equals(cookie.getName()))
                    .findFirst()
                    .map(Cookie::getValue)
                    .ifPresentOrElse(uv::set, generateNewCookieTask);
        } else {
            generateNewCookieTask.run();
        }
        return ShortLinkStatsRecordEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .fullShortUrl(fullShortUrl)
                .gid(gid)
                .remoteAddr(LinkUtil.getActualIp((HttpServletRequest) request))
                .os(LinkUtil.getOs((HttpServletRequest) request))
                .browser(LinkUtil.getBrowser((HttpServletRequest) request))
                .device(LinkUtil.getDevice((HttpServletRequest) request))
                .network(LinkUtil.getNetwork((HttpServletRequest) request))
                .uv(uv.get())
                .currentDate(new Date())
                .build();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ShortLinkCacheObj {
        private String originUrl;
        private String gid;
        private Date validDate;
    }
}
