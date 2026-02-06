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

import com.xhy.shortlink.framework.starter.cache.DistributedCache;
import com.xhy.shortlink.framework.starter.idempotent.core.IdempotentAspect;
import com.xhy.shortlink.framework.starter.idempotent.core.param.IdempotentParamExecuteHandler;
import com.xhy.shortlink.framework.starter.idempotent.core.param.IdempotentParamService;
import com.xhy.shortlink.framework.starter.idempotent.core.spel.IdempotentSpELByMQExecuteHandler;
import com.xhy.shortlink.framework.starter.idempotent.core.spel.IdempotentSpELByRestAPIExecuteHandler;
import com.xhy.shortlink.framework.starter.idempotent.core.spel.IdempotentSpELService;
import com.xhy.shortlink.framework.starter.idempotent.core.token.IdempotentTokenController;
import com.xhy.shortlink.framework.starter.idempotent.core.token.IdempotentTokenExecuteHandler;
import com.xhy.shortlink.framework.starter.idempotent.core.token.IdempotentTokenService;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 幂等自动装配
 */
@EnableConfigurationProperties(IdempotentProperties.class)
public class IdempotentAutoConfiguration {

    @Bean
    public IdempotentAspect idempotentAspect() {
        return new IdempotentAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentParamService idempotentParamExecuteHandler(RedissonClient redissonClient) {
        return new IdempotentParamExecuteHandler(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentTokenService idempotentTokenExecuteHandler(DistributedCache distributedCache,
                                                                IdempotentProperties idempotentProperties) {
        return new IdempotentTokenExecuteHandler(distributedCache, idempotentProperties);
    }

    @Bean
    public IdempotentTokenController idempotentTokenController(IdempotentTokenService idempotentTokenService) {
        return new IdempotentTokenController(idempotentTokenService);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentSpELService idempotentSpELByRestAPIExecuteHandler(RedissonClient redissonClient) {
        return new IdempotentSpELByRestAPIExecuteHandler(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentSpELByMQExecuteHandler idempotentSpELByMQExecuteHandler(DistributedCache distributedCache,
                                                                              IdempotentProperties idempotentProperties) {
        return new IdempotentSpELByMQExecuteHandler(distributedCache, idempotentProperties);
    }
}
