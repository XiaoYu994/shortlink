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

import com.xhy.shortlink.framework.starter.user.core.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * OpenFeign 全局配置，负责在跨服务调用时透传用户上下文信息
 *
 * @author XiaoYu
 */
public class OpenFeignConfiguration {

    /**
     * 注册请求拦截器，将当前用户信息通过 HTTP Header 传递到下游服务
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            template.header("username", UserContext.getUsername());
            template.header("userId", UserContext.getUserId());
            template.header("realName", UserContext.getRealName());
        };
    }
}
