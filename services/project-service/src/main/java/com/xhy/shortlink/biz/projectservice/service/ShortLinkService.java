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

package com.xhy.shortlink.biz.projectservice.service;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * 短链接门面：对外保持统一入口，委托给创建、修改、查询与跳转子服务
 *
 * @author XiaoYu
 */
public interface ShortLinkService extends ShortLinkCoreService {

    /**
     * 短链接跳转
     *
     * @param shortUri 短链 URI
     * @param request  原始请求
     * @param response 原始响应
     */
    void redirect(String shortUri, ServletRequest request, ServletResponse response);
}
