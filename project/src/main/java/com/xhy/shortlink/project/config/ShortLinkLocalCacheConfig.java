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

package com.xhy.shortlink.project.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/*
* caffeine 本地缓存配置类
* */
@Configuration
public class ShortLinkLocalCacheConfig {

    @Bean
    public Cache<String, String> shortLinkCache() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(10000) // 存1万条热点数据，大概占用 10MB-20MB 内存，很安全
                .expireAfterWrite(30, TimeUnit.MINUTES) // 写入半小时后过期，避免频繁失效导致穿透到 Redis
                .recordStats() // 开启统计，生产环境可以通过监控看到缓存命中率
                .build();
    }
}
