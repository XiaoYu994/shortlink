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

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    @Value("${short-link.stats.locale.amap-key:}")
    private String statsLocaleAmapKey;

    /**
     * 不发 HTTP：内网、空 IP、或 Redis 已有结果。
     */
    public Optional<IpLocation> peekWithoutHttp(String ip) {
        if (StrUtil.isBlank(ip) || isPrivateOrLocal(ip)) {
            return Optional.of(IpLocation.unknown());
        }
        return readCache(ip);
    }

    /**
     * 含高德 HTTP，供异步线程调用。结果写入 Redis。
     */
    public IpLocation resolveRemote(String ip) {
        Optional<IpLocation> peeked = peekWithoutHttp(ip);
        if (peeked.isPresent()) {
            return peeked.get();
        }
        if (StrUtil.isBlank(statsLocaleAmapKey)) {
            return IpLocation.unknown();
        }
        IpLocation resolved = queryAmap(ip);
        writeCache(ip, resolved);
        return resolved;
    }

    static boolean isPrivateOrLocal(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (Exception ex) {
            return true;
        }
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
            JSONObject json = JSON.parseObject(body);
            if (json == null || !AMAP_SUCCESS_CODE.equals(json.getString("infocode"))) {
                return IpLocation.unknown();
            }
            return new IpLocation(
                    textOrUnknown(json.getString("province")),
                    textOrUnknown(json.getString("city")),
                    textOrUnknown(json.getString("adcode")));
        } catch (Exception ex) {
            log.warn("IP解析失败或超时, IP: {}", ip, ex);
            return IpLocation.unknown();
        }
    }

    private static String textOrUnknown(String value) {
        if (StrUtil.isBlank(value) || AMAP_EMPTY_VALUE.equals(value)) {
            return LOCALE_UNKNOWN;
        }
        return value;
    }
}
