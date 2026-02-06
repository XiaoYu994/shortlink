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

package com.xhy.shortlink.framework.starter.idempotent.core.spel;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import com.xhy.shortlink.framework.starter.cache.DistributedCache;
import com.xhy.shortlink.framework.starter.idempotent.annotation.Idempotent;
import com.xhy.shortlink.framework.starter.idempotent.config.IdempotentProperties;
import com.xhy.shortlink.framework.starter.idempotent.core.AbstractIdempotentExecuteHandler;
import com.xhy.shortlink.framework.starter.idempotent.core.IdempotentAspect;
import com.xhy.shortlink.framework.starter.idempotent.core.IdempotentContext;
import com.xhy.shortlink.framework.starter.idempotent.core.IdempotentParamWrapper;
import com.xhy.shortlink.framework.starter.idempotent.core.RepeatConsumptionException;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentMQConsumeStatusEnum;
import com.xhy.shortlink.framework.starter.idempotent.toolkit.LogUtil;
import com.xhy.shortlink.framework.starter.idempotent.toolkit.SpELUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 SpEL 方法验证请求幂等性，适用于 MQ 场景
 */
@RequiredArgsConstructor
public final class IdempotentSpELByMQExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentSpELService {

    private static final String WRAPPER = "wrapper:spEL:MQ";
    private static final String LUA_SCRIPT_PATH = "lua/set_if_absent_and_get.lua";

    private static final DefaultRedisScript<String> SET_IF_ABSENT_SCRIPT;

    static {
        SET_IF_ABSENT_SCRIPT = new DefaultRedisScript<>();
        SET_IF_ABSENT_SCRIPT.setScriptSource(
                new ResourceScriptSource(new ClassPathResource(LUA_SCRIPT_PATH)));
        SET_IF_ABSENT_SCRIPT.setResultType(String.class);
    }

    private final DistributedCache distributedCache;
    private final IdempotentProperties idempotentProperties;

    @SneakyThrows
    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        Idempotent idempotent = IdempotentAspect.getIdempotent(joinPoint);
        String key = (String) SpELUtil.parseKey(idempotent.key(),
                ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
        return IdempotentParamWrapper.builder().lockKey(key).joinPoint(joinPoint).build();
    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        String uniqueKey = wrapper.getIdempotent().uniqueKeyPrefix() + wrapper.getLockKey();
        int timeout = idempotentProperties.getMq().getConsumingTimeout();
        String absentAndGet = setIfAbsentAndGet(uniqueKey,
                IdempotentMQConsumeStatusEnum.CONSUMING.getCode(), timeout, TimeUnit.SECONDS);

        if (Objects.nonNull(absentAndGet)) {
            boolean error = IdempotentMQConsumeStatusEnum.isError(absentAndGet);
            LogUtil.getLog(wrapper.getJoinPoint()).warn("[{}] MQ repeated consumption, {}.",
                    uniqueKey, error ? "Wait for the client to delay consumption" : "Status is completed");
            throw new RepeatConsumptionException(error);
        }
        IdempotentContext.put(WRAPPER, wrapper);
    }

    private String setIfAbsentAndGet(String key, String value, long timeout, TimeUnit timeUnit) {
        long millis = timeUnit.toMillis(timeout);
        return ((StringRedisTemplate) distributedCache.getInstance())
                .execute(SET_IF_ABSENT_SCRIPT, List.of(key), value, String.valueOf(millis));
    }

    @Override
    public void exceptionProcessing() {
        IdempotentParamWrapper wrapper = (IdempotentParamWrapper) IdempotentContext.getKey(WRAPPER);
        if (wrapper != null) {
            Idempotent idempotent = wrapper.getIdempotent();
            String uniqueKey = idempotent.uniqueKeyPrefix() + wrapper.getLockKey();
            try {
                distributedCache.delete(uniqueKey);
            } catch (Throwable ex) {
                LogUtil.getLog(wrapper.getJoinPoint())
                        .error("[{}] Failed to del MQ anti-heavy token.", uniqueKey, ex);
            }
        }
    }

    @Override
    public void postProcessing() {
        IdempotentParamWrapper wrapper = (IdempotentParamWrapper) IdempotentContext.getKey(WRAPPER);
        if (wrapper != null) {
            Idempotent idempotent = wrapper.getIdempotent();
            String uniqueKey = idempotent.uniqueKeyPrefix() + wrapper.getLockKey();
            try {
                distributedCache.put(uniqueKey,
                        IdempotentMQConsumeStatusEnum.CONSUMED.getCode(),
                        idempotent.keyTimeout(), TimeUnit.SECONDS);
            } catch (Throwable ex) {
                LogUtil.getLog(wrapper.getJoinPoint())
                        .error("[{}] Failed to set MQ anti-heavy token.", uniqueKey, ex);
            }
        }
    }
}
