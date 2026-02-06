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

package com.xhy.shortlink.framework.starter.idempotent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 幂等属性配置
 */
@Data
@ConfigurationProperties(prefix = IdempotentProperties.PREFIX)
public class IdempotentProperties {

    public static final String PREFIX = "framework.idempotent";

    private Token token = new Token();

    private Mq mq = new Mq();

    @Data
    public static class Token {

        /**
         * Token 幂等 Key 前缀
         */
        private String prefix;

        /**
         * Token 申请后过期时间，单位毫秒，默认 6000ms
         */
        private Long timeout = 6000L;
    }

    @Data
    public static class Mq {

        /**
         * MQ 消费中状态超时时间，单位秒，默认 600s
         */
        private Integer consumingTimeout = 600;
    }
}
