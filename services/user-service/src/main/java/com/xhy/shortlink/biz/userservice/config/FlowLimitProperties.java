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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户级访问限流配置
 *
 * @author XiaoYu
 */
@Data
@ConfigurationProperties(prefix = "short-link.flow-limit")
public class FlowLimitProperties {

    /** 是否开启限流 */
    private Boolean enable = true;

    /** 时间窗口（秒） */
    private Integer timeWindow = 1;

    /** 窗口内最大访问次数 */
    private Integer maxAccessCount = 20;

    /** Redis 不可用时是否放行，安全接口默认拒绝 */
    private Boolean failOpen = false;
}
