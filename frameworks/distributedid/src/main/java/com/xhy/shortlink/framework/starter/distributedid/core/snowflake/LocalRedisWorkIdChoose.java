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

package com.xhy.shortlink.framework.starter.distributedid.core.snowflake;

import cn.hutool.core.collection.CollUtil;
import com.xhy.shortlink.framework.starter.bases.ApplicationContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Redis 的 WorkId 分配策略
 * <p>
 * 通过 Lua 脚本原子递增 Redis Hash 分配 WorkId 和 DatacenterId，
 * 范围 [0, 31]，满后归零循环。Redis 异常时降级到随机策略
 */
@Slf4j
public class LocalRedisWorkIdChoose extends AbstractWorkIdChooseTemplate implements InitializingBean {

    private final RedisTemplate stringRedisTemplate;

    public LocalRedisWorkIdChoose() {
        this.stringRedisTemplate = ApplicationContextHolder.getBean(StringRedisTemplate.class);
    }

    /**
     * 通过 Redis Lua 脚本原子分配 WorkId：
     * 1. 执行 chooseWorkIdLua.lua 脚本
     * 2. 返回 [workId, dataCenterId] 列表
     * 3. 异常时降级到 RandomWorkIdChoose
     */
    @Override
    @SuppressWarnings("unchecked")
    public WorkIdWrapper chooseWorkId() {
        DefaultRedisScript redisScript = new DefaultRedisScript();
        redisScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/chooseWorkIdLua.lua")));
        List<Long> luaResultList = null;
        try {
            redisScript.setResultType(List.class);
            luaResultList = (ArrayList) this.stringRedisTemplate.execute(redisScript, null);
        } catch (Exception ex) {
            log.error("Redis Lua 脚本获取 WorkId 失败", ex);
        }
        return CollUtil.isNotEmpty(luaResultList)
                ? new WorkIdWrapper(luaResultList.get(0), luaResultList.get(1))
                : new RandomWorkIdChoose().chooseWorkId();
    }

    /**
     * Bean 初始化回调，触发模板方法完成 Snowflake 初始化
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        chooseAndInit();
    }
}
