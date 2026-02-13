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
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;

import java.net.URI;
import java.util.Date;

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
                    domain = host.substring(4);
                }
            }
        } catch (Exception ignored) {
        }
        return domain;
    }
}
