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

package com.xhy.shortlink.biz.statsservice.locale;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.LOCALE_IP_KEY;
import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.AMAP_EMPTY_VALUE;
import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;
import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.AMAP_SUCCESS_CODE;
import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.LOCALE_UNKNOWN;

/**
 * IP 定位：内网/缓存命中可同步返回；高德 HTTP 只给异步补全用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpLocationService {

    private static final int AMAP_REQUEST_TIMEOUT_MILLIS = 3000;
    private static final long CACHE_TTL_HOURS = 24;

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, CompletableFuture<IpLocation>> inflight = new ConcurrentHashMap<>();

    @Value("${short-link.stats.locale.amap-key:}")
    private String statsLocaleAmapKey;

    /**
     * 不发 HTTP：内网、空 IP、非字面量 IP、或 Redis 已有结果。
     */
    public Optional<IpLocation> peekWithoutHttp(String ip) {
        if (StrUtil.isBlank(ip) || isPrivateOrLocal(ip)) {
            return Optional.of(IpLocation.unknown());
        }
        return readCache(ip);
    }

    /**
     * 含高德 HTTP，供异步线程调用。结果写入 Redis；同 IP 并发只打一次高德。
     */
    public IpLocation resolveRemote(String ip) {
        Optional<IpLocation> cached = readCache(ip);
        if (cached.isPresent()) {
            return cached.get();
        }
        if (StrUtil.isBlank(statsLocaleAmapKey) || StrUtil.isBlank(ip) || isPrivateOrLocal(ip)) {
            return IpLocation.unknown();
        }
        CompletableFuture<IpLocation> created = new CompletableFuture<>();
        CompletableFuture<IpLocation> existing = inflight.putIfAbsent(ip, created);
        if (existing != null) {
            try {
                return existing.get(AMAP_REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                return IpLocation.unknown();
            }
        }
        try {
            IpLocation resolved = queryAmap(ip);
            writeCache(ip, resolved);
            created.complete(resolved);
            return resolved;
        } catch (Exception ex) {
            IpLocation unknown = IpLocation.unknown();
            created.complete(unknown);
            return unknown;
        } finally {
            inflight.remove(ip);
        }
    }

    static boolean isPrivateOrLocal(String ip) {
        if (StrUtil.isBlank(ip)) {
            return true;
        }
        if (ip.indexOf(':') >= 0) {
            String lower = ip.toLowerCase();
            return "::1".equals(lower)
                    || "::".equals(lower)
                    || lower.startsWith("fe80:")
                    || lower.startsWith("fc")
                    || lower.startsWith("fd");
        }
        if (!isLiteralIpv4(ip)) {
            return true;
        }
        String[] parts = ip.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        if (first == 10 || first == 127 || first == 0) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        return first == 100 && second >= 64 && second <= 127;
    }

    static boolean isLiteralIpv4(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String octet : parts) {
            if (octet.isEmpty() || octet.length() > 3) {
                return false;
            }
            int value = 0;
            for (int i = 0; i < octet.length(); i++) {
                char ch = octet.charAt(i);
                if (ch < '0' || ch > '9') {
                    return false;
                }
                value = value * 10 + (ch - '0');
            }
            if (value > 255) {
                return false;
            }
        }
        return true;
    }

    private Optional<IpLocation> readCache(String ip) {
        String raw = stringRedisTemplate.opsForValue().get(String.format(LOCALE_IP_KEY, ip));
        if (StrUtil.isBlank(raw)) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\|", 3);
        if (parts.length < 3) {
            return Optional.empty();
        }
        return Optional.of(new IpLocation(parts[0], parts[1], parts[2]));
    }

    private void writeCache(String ip, IpLocation location) {
        stringRedisTemplate.opsForValue().set(
                String.format(LOCALE_IP_KEY, ip),
                location.province() + "|" + location.city() + "|" + location.adcode(),
                CACHE_TTL_HOURS,
                TimeUnit.HOURS);
    }

    IpLocation queryAmap(String ip) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", statsLocaleAmapKey);
        params.put("ip", ip);
        try {
            String body = HttpUtil.get(AMAP_REMOTE_URL, params, AMAP_REQUEST_TIMEOUT_MILLIS);
            return parseAmapBody(body);
        } catch (Exception ex) {
            log.warn("IP解析失败或超时, IP: {}", ip, ex);
            return IpLocation.unknown();
        }
    }

    static IpLocation parseAmapBody(String body) {
        JSONObject json = JSON.parseObject(body);
        if (json == null || !AMAP_SUCCESS_CODE.equals(json.getString("infocode"))) {
            return IpLocation.unknown();
        }
        return new IpLocation(
                textOrUnknown(json.getString("province")),
                textOrUnknown(json.getString("city")),
                textOrUnknown(json.getString("adcode")));
    }

    private static String textOrUnknown(String value) {
        if (StrUtil.isBlank(value) || AMAP_EMPTY_VALUE.equals(value)) {
            return LOCALE_UNKNOWN;
        }
        return value;
    }
}
