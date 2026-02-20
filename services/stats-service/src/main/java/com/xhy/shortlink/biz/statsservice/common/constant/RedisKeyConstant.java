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

package com.xhy.shortlink.biz.statsservice.common.constant;

/**
 * Redis 键常量（stats-service 使用）
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /** 短链接修改分组 ID 锁前缀 Key */
    public static final String LOCK_GID_UPDATE_KEY = "short-link:lock:update-gid:%s";

    /** 统计今日 PV/UV/UIP 排行榜 Key */
    public static final String RANK_KEY = "short-link:rank:%s:{%s}:%s";

    /** 今日 UV HyperLogLog Key */
    public static final String TODAY_UV_HLL_KEY = "short-link:stats:uv:hll:%s:%s";

    /** 历史总 UV HyperLogLog Key */
    public static final String TOTAL_UV_HLL_KEY = "short-link:stats:uv:hll:total:%s";

    /** 今日 UIP HyperLogLog Key */
    public static final String TODAY_UIP_HLL_KEY = "short-link:stats:uip:hll:%s:%s";

    /** 历史总 UIP HyperLogLog Key */
    public static final String TOTAL_UIP_HLL_KEY = "short-link:stats:uip:hll:total:%s";
}
