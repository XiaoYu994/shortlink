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

package com.xhy.shortlink.framework.starter.cache.toolkit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

/**
 * INCR 与 EXPIRE 必须同脚本完成，避免计数留下无 TTL 的 key。
 */
public final class RedisIncrWithExpire {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 or redis.call('TTL', KEYS[1]) < 0 then "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) "
                    + "end "
                    + "return current",
            Long.class);

    private RedisIncrWithExpire() {
    }

    public static Long increment(StringRedisTemplate redisTemplate, String key, long expireSeconds) {
        return redisTemplate.execute(SCRIPT, Collections.singletonList(key), String.valueOf(expireSeconds));
    }
}
