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

package com.xhy.shortlink.framework.starter.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多级缓存配置属性，控制 L1 本地缓存的容量和过期策略
 */
@Data
@ConfigurationProperties(prefix = MultistageCacheProperties.PREFIX)
public class MultistageCacheProperties {

    public static final String PREFIX = "framework.cache.multistage";

    /**
     * 是否启用多级缓存
     */
    private Boolean enabled = false;

    /**
     * L1 本地缓存初始容量
     */
    private Integer initialCapacity = 100;

    /**
     * L1 本地缓存最大容量
     */
    private Long maximumSize = 10000L;

    /**
     * L1 本地缓存写入后过期时间（分钟）
     */
    private Long expireAfterWrite = 30L;
}
