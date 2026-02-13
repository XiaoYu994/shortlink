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

package com.xhy.shortlink.biz.projectservice.common.constant;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 键常量
 *
 * @author XiaoYu
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /** 短链接跳转前缀 Key */
    public static final String GOTO_SHORT_LINK_KEY = "short-link:goto:%s:";

    /** 短链接空值缓存 Key */
    public static final String GOTO_IS_NULL_SHORT_LINK_KEY = "short-link:goto:is-null:%s:";

    /** 短链接空缓存过期时间（秒），加随机偏移防止缓存雪崩 */
    public static final long DEFAULT_CACHE_VALID_TIME_FOR_GOTO = 30 + ThreadLocalRandom.current().nextInt(10);

    /** 短链接跳转锁前缀 Key */
    public static final String LOCK_GOTO_SHORT_LINK_KEY = "short-link:lock:goto:%s:";

    /** 分布式锁创建短链接 Key */
    public static final String SHORT_LINK_CREATE_LOCK_KEY = "short-link:create-lock";
}
