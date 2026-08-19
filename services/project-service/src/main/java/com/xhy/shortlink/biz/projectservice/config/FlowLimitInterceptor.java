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

package com.xhy.shortlink.biz.projectservice.config;

import com.xhy.shortlink.framework.starter.cache.toolkit.RedisIncrWithExpire;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 创建短链接口限流
 *
 * @author XiaoYu
 */
@RequiredArgsConstructor
public class FlowLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final FlowLimitProperties flowLimitProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!Boolean.TRUE.equals(flowLimitProperties.getEnable())) {
            return true;
        }
        String identity = resolveIdentity(request);
        String key = "short-link:flow-limit:svc:" + identity + ":" + request.getRequestURI();
        Long current;
        try {
            current = RedisIncrWithExpire.increment(
                    stringRedisTemplate, key, flowLimitProperties.getTimeWindow());
        } catch (RuntimeException ex) {
            if (Boolean.TRUE.equals(flowLimitProperties.getFailOpen())) {
                return true;
            }
            throw new ClientException("限流服务暂不可用，请稍后再试");
        }
        if (current == null) {
            if (Boolean.TRUE.equals(flowLimitProperties.getFailOpen())) {
                return true;
            }
            throw new ClientException("限流服务暂不可用，请稍后再试");
        }
        if (current != null && current > flowLimitProperties.getMaxAccessCount()) {
            throw new ClientException("访问过于频繁，请稍后再试");
        }
        return true;
    }

    private String resolveIdentity(HttpServletRequest request) {
        String username = UserContext.getUsername();
        if (StringUtils.hasText(username)) {
            return username;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
