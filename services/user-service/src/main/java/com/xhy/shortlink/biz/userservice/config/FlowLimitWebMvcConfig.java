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

package com.xhy.shortlink.biz.userservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册用户侧限流拦截器
 *
 * @author XiaoYu
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FlowLimitProperties.class)
public class FlowLimitWebMvcConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;
    private final FlowLimitProperties flowLimitProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new FlowLimitInterceptor(stringRedisTemplate, flowLimitProperties))
                .addPathPatterns(
                        "/api/short-link/admin/v1/user",
                        "/api/short-link/admin/v1/user/login",
                        "/api/short-link/admin/v1/create",
                        "/api/short-link/admin/v1/create/batch");
    }
}
