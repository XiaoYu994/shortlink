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

package com.xhy.shortlink.framework.starter.distributedid.core.serviceid;

import com.xhy.shortlink.framework.starter.distributedid.core.IdGenerator;
import com.xhy.shortlink.framework.starter.distributedid.core.snowflake.SnowflakeIdInfo;
import com.xhy.shortlink.framework.starter.distributedid.toolkit.SnowflakeIdUtil;

/**
 * 默认业务 ID 生成器（基因法）
 * <p>
 * 将 12 位序列号拆为 8 位真实序列 + 4 位业务基因，
 * 把 serviceId 的 hash 嵌入低 4 位，使 ID 天然携带分表路由信息
 */
public final class DefaultServiceIdGenerator implements ServiceIdGenerator {

    private final IdGenerator idGenerator;
    private final long maxBizIdBitsLen;

    /**
     * 工作 ID 5 bit
     */
    private static final long WORKER_ID_BITS = 5L;

    /**
     * 数据中心 ID 5 bit
     */
    private static final long DATA_CENTER_ID_BITS = 5L;

    /**
     * 序列号总共 12 bit
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 真实序列号占 8 bit
     */
    private static final long SEQUENCE_ACTUAL_BITS = 8L;

    /**
     * 业务基因占 4 bit
     */
    private static final long SEQUENCE_BIZ_BITS = 4L;

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;
    private static final long DEFAULT_TWEPOCH = 1288834974657L;

    public DefaultServiceIdGenerator() {
        this(SEQUENCE_BIZ_BITS);
    }

    public DefaultServiceIdGenerator(long serviceIdBitLen) {
        idGenerator = SnowflakeIdUtil.getInstance();
        this.maxBizIdBitsLen = (long) Math.pow(2, serviceIdBitLen);
    }

    /**
     * 基因法生成 ID：
     * 1. 对 serviceId 取 hash 后对 2^4 取模，得到 4 位业务基因
     * 2. 生成标准雪花 ID
     * 3. 将业务基因 OR 到 ID 低位
     */
    @Override
    public long nextId(long serviceId) {
        long id = Math.abs(Long.valueOf(serviceId).hashCode()) % (this.maxBizIdBitsLen);
        long nextId = idGenerator.nextId();
        return nextId | id;
    }

    @Override
    public String nextIdStr(long serviceId) {
        return Long.toString(nextId(serviceId));
    }

    /**
     * 反解析带基因的雪花 ID：
     * 低 4 位为业务基因（gene），接下来 8 位为真实序列号
     */
    @Override
    public SnowflakeIdInfo parseSnowflakeId(long snowflakeId) {
        return SnowflakeIdInfo.builder()
                .workerId((int) ((snowflakeId >> WORKER_ID_SHIFT) & ~(-1L << WORKER_ID_BITS)))
                .dataCenterId((int) ((snowflakeId >> DATA_CENTER_ID_SHIFT) & ~(-1L << DATA_CENTER_ID_BITS)))
                .timestamp((snowflakeId >> TIMESTAMP_LEFT_SHIFT) + DEFAULT_TWEPOCH)
                .sequence((int) ((snowflakeId >> SEQUENCE_BIZ_BITS) & ~(-1L << SEQUENCE_ACTUAL_BITS)))
                .gene((int) (snowflakeId & ~(-1L << SEQUENCE_BIZ_BITS)))
                .build();
    }
}
