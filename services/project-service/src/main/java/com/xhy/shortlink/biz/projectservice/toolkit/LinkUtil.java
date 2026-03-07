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

package com.xhy.shortlink.biz.projectservice.toolkit;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.useragent.UserAgentUtil;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Date;
import java.util.Map;

import static com.xhy.shortlink.biz.projectservice.common.constant.ShortLinkConstant.DEFAULT_CACHE_VALID_TIME;

/**
 * 短链接工具类（仅包含创建接口所需方法）
 *
 * @author XiaoYu
 */
public final class LinkUtil {

    private LinkUtil() {
    }

    /**
     * 获取缓存有效期
     *
     * @param validDate 短链接的数据库截止日期
     * @return Redis 的 TTL（毫秒）
     */
    public static long getLinkCacheValidTime(Date validDate) {
        if (validDate == null) {
            return DEFAULT_CACHE_VALID_TIME;
        }
        long timeToLive = validDate.getTime() - System.currentTimeMillis();
        if (timeToLive <= 0) {
            throw new ClientException("短链接已过期");
        }
        return Math.min(timeToLive, DEFAULT_CACHE_VALID_TIME);
    }

    /**
     * 获取原始链接中的域名，去掉 www 前缀
     *
     * @param url 原始链接
     * @return 域名
     */
    public static String extractDomain(String url) {
        String domain = null;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (StrUtil.isNotBlank(host)) {
                domain = host;
                if (domain.startsWith("www.")) {
                    domain = host.substring("www.".length());
                }
            }
        } catch (Exception ignored) {
        }
        return domain;
    }

    /**
     * 获取用户真实 IP
     */
    public static String getActualIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    /**
     * 获取用户访问操作系统
     */
    public static String getOs(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent").toLowerCase();
        Map<String, String> osMapping = Map.of(
                "windows", "Windows",
                "mac", "Mac OS",
                "linux", "Linux",
                "android", "Android",
                "iphone", "iOS",
                "ipad", "iOS");
        return osMapping.entrySet().stream()
                .filter(entry -> ua.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("Unknown");
    }

    /**
     * 获取用户访问浏览器
     */
    public static String getBrowser(HttpServletRequest request) {
        String browser = UserAgentUtil.parse(request.getHeader("User-Agent")).getBrowser().toString();
        if (StrUtil.isEmpty(browser)) {
            return "未知";
        }
        return browser;
    }

    /**
     * 获取用户访问设备
     */
    public static String getDevice(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.toLowerCase().contains("mobile")) {
            return "Mobile";
        }
        return "PC";
    }

    /**
     * 获取用户访问网络
     */
    public static String getNetwork(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return "Unknown";
        }
        String uaUpper = ua.toUpperCase();
        if (uaUpper.contains("WIFI")) {
            return "WIFI";
        }
        if (uaUpper.contains("MOBILE") || uaUpper.contains("ANDROID") || uaUpper.contains("IPHONE")) {
            return "Mobile";
        }
        return "WIFI";
    }
}
