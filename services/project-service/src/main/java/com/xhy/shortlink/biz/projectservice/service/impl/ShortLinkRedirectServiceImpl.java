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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.xhy.shortlink.biz.projectservice.config.ColdDataProperties;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(ColdDataProperties.class)
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
    private final ColdDataProperties coldDataProperties;

    @Value("${short-link.domain.default}")
    private String defaultDomain;

    /**
     * 短链接跳转入口：多级缓存 → 防穿透校验 → 加锁回源
     */
    @SneakyThrows
    public void redirect(String shortUri, ServletRequest request, ServletResponse response) {
        String fullShortUrl = defaultDomain + "/" + shortUri;
        // 1. 优先从 L1 Caffeine / L2 Redis 读取缓存
        ShortLinkCacheObj cacheObj = getFromCache(fullShortUrl);
        if (cacheObj != null) {
            executeRedirect(fullShortUrl, cacheObj, request, response);
            return;
        }
        // 2. 布隆过滤器 + 空值缓存双重防穿透
        if (isPossiblePenetration(fullShortUrl)) {
            ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
            return;
        }
        // 3. 缓存未命中且通过防穿透校验，加分布式锁回源 DB
        processWithLock(fullShortUrl, request, response);
    }

    /**
     * 多级缓存读取：L1 Caffeine → L2 Redis，命中后解析并校验有效期
     */
    private ShortLinkCacheObj getFromCache(String fullShortUrl) {
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        // L1 本地缓存查询
        String composite = shortLinkCache.getIfPresent(key);
        if (CharSequenceUtil.isBlank(composite)) {
            // L1 未命中，降级到 L2 Redis
            composite = stringRedisTemplate.opsForValue().get(key);
            if (CharSequenceUtil.isBlank(composite)) {
                return null;
            }
            // 回填 L1 缓存
            shortLinkCache.put(key, composite);
        }
        // 解析缓存值（格式：validTimeStamp|originUrl|gid），校验有效期
        ShortLinkCacheObj cacheObj = parseCache(composite, key);
        if (cacheObj == null) {
            // 解析失败或已过期，清除两级缓存中的脏数据
            shortLinkCache.invalidate(key);
            stringRedisTemplate.delete(key);
        }
        return cacheObj;
    }

    /**
     * 缓存穿透检测：布隆过滤器不存在 或 空值缓存命中 → 判定为穿透
     */
    private boolean isPossiblePenetration(String fullShortUrl) {
        if (!cacheHelper.bloomFilterContains(fullShortUrl)) {
            return true;
        }
        // 布隆过滤器存在但之前回源为空，短时间内用空值缓存拦截
        String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
        return CharSequenceUtil.isNotBlank(stringRedisTemplate.opsForValue().get(keyIsNull));
    }

    /**
     * 加分布式锁回源 DB，防止缓存击穿（热点 key 失效时大量请求同时穿透）
     */
    private void processWithLock(String fullShortUrl, ServletRequest request, ServletResponse response) throws IOException {
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            // Double-Check：获锁后再查一次缓存，可能已被其他线程重建
            ShortLinkCacheObj cacheObj = getFromCache(fullShortUrl);
            if (cacheObj != null) {
                executeRedirect(fullShortUrl, cacheObj, request, response);
                return;
            }
            if (isPossiblePenetration(fullShortUrl)) {
                ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
                return;
            }
            // 回源 DB 查询（热表 → 冷表）
            ShortLinkCacheObj dbObj = loadFromDb(fullShortUrl);
            if (dbObj != null) {
                cacheHelper.rebuildCache(fullShortUrl, dbObj.getOriginUrl(), dbObj.getGid(), dbObj.getValidDate());
                // 冷表命中时累加回温计数，达到阈值则迁回热表
                if (dbObj.isFromCold()) {
                    tryRehot(fullShortUrl, dbObj.getGid());
                }
                executeRedirect(fullShortUrl, dbObj, request, response);
            } else {
                // DB 也不存在，写入空值缓存防止后续穿透
                String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
                stringRedisTemplate.opsForValue().set(keyIsNull, "-", DEFAULT_CACHE_VALID_TIME_FOR_GOTO, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 回源 DB 查询：先查热表（t_link_goto → t_link），未命中再查冷表（t_link_goto_cold → t_link_cold）
     */
    private ShortLinkCacheObj loadFromDb(String fullShortUrl) {
        // 热表路由：通过 goto 表获取 gid，再用 gid 分片查询短链接主表
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
        // 冷表兜底：热表无记录时查询冷数据库
        ShortLinkGoToColdDO coldGoToDO = shortLinkGoToColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
        if (coldGoToDO != null) {
            ShortLinkColdDO coldDO = shortLinkColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkColdDO::getGid, coldGoToDO.getGid())
                    .eq(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getCode()));
            if (coldDO != null && isNotExpired(coldDO.getValidDate())) {
                return new ShortLinkCacheObj(coldDO.getOriginUrl(), coldDO.getGid(), coldDO.getValidDate(), true);
            }
        }
        return null;
    }

    /**
     * 执行跳转：异步发送统计事件 → 302 重定向到原始链接
     */
    @SneakyThrows
    private void executeRedirect(String fullShortUrl, ShortLinkCacheObj cacheObj, ServletRequest request, ServletResponse response) {
        statsProducer.sendMessage(buildLinkStatsRecordDTO(fullShortUrl, cacheObj.getGid(), request, response));
        ((HttpServletResponse) response).sendRedirect(cacheObj.getOriginUrl());
    }

    /** 判断链接是否未过期：validDate 为 null 表示永久有效 */
    private boolean isNotExpired(Date validDate) {
        return validDate == null || validDate.after(new Date());
    }

    /** 冷链接回温：累加访问计数，达到阈值时迁回热表 */
    private void tryRehot(String fullShortUrl, String gid) {
        try {
            String key = String.format(SHORT_LINK_COLD_REHOT_KEY, fullShortUrl);
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
            }
            if (count != null && count >= coldDataProperties.getRehot().getThreshold()) {
                rehotColdLink(fullShortUrl, gid);
                stringRedisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.error("[回温] 失败，fullShortUrl={}", fullShortUrl, e);
        }
    }

    /** 将冷表链接迁回热表 */
    private void rehotColdLink(String fullShortUrl, String gid) {
        ShortLinkColdDO coldDO = shortLinkColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                .eq(ShortLinkColdDO::getGid, gid)
                .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl));
        if (coldDO == null) {
            return;
        }
        shortLinkMapper.insert(BeanUtil.toBean(coldDO, ShortLinkDO.class));
        ShortLinkGoToColdDO goToCold = shortLinkGoToColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
        if (goToCold != null) {
            shortLinkGoToMapper.insert(BeanUtil.toBean(goToCold, ShortLinkGoToDO.class));
            shortLinkGoToColdMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                    .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
        }
        shortLinkColdMapper.delete(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                .eq(ShortLinkColdDO::getGid, gid)
                .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl));
        log.info("[回温] 完成，fullShortUrl={}", fullShortUrl);
    }

    /**
     * 解析缓存值（格式：validTimeStamp|originUrl|gid），校验有效期并续期 Redis TTL
     */
    private ShortLinkCacheObj parseCache(String composite, String key) {
        String[] split = composite.split("\\|");
        if (split.length < 3) {
            return null;
        }
        long validTime = Long.parseLong(split[0]);
        String originalLink = split[1];
        String gid = split[2];
        // 有效期已过，清除 Redis 缓存
        if (validTime != -1 && System.currentTimeMillis() > validTime) {
            stringRedisTemplate.delete(key);
            return null;
        }
        // 动态续期：永久链接固定 1 天，有期限链接取剩余时间与 1 天的较小值
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

    /**
     * 构建统计事件：提取访问者信息 + UV Cookie 去重
     */
    private ShortLinkStatsRecordEvent buildLinkStatsRecordDTO(String fullShortUrl, String gid, ServletRequest request, ServletResponse response) {
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        AtomicReference<String> uv = new AtomicReference<>();
        // 生成新 UV Cookie 并记录到 Redis Set 的任务（首次访问或 Cookie 丢失时执行）
        Runnable generateNewCookieTask = () -> {
            uv.set(UUID.fastUUID().toString());
            Cookie cookie = new Cookie("uv", uv.get());
            cookie.setMaxAge(DEFAULT_COOKIE_VALID_TIME);
            cookie.setPath("/");
            ((HttpServletResponse) response).addCookie(cookie);
            uvFirstFlag.set(true);
            stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, uv.get());
        };
        // 已有 uv Cookie 则复用，否则生成新的
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

    /** 缓存值对象：封装从缓存/DB 中获取的跳转所需数据 */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ShortLinkCacheObj {
        /** 原始目标链接 */
        private String originUrl;
        /** 短链接所属分组标识 */
        private String gid;
        /** 有效期，null 表示永久有效 */
        private Date validDate;
        /** 是否来自冷表查询 */
        private boolean fromCold;

        ShortLinkCacheObj(String originUrl, String gid, Date validDate) {
            this(originUrl, gid, validDate, false);
        }
    }
}
