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

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.xhy.shortlink.framework.starter.distributedid.core.IdGenerator;

import java.io.Serializable;
import java.util.Date;

/**
 * 雪花算法 ID 生成器
 * <p>
 * 64 位结构：1 符号位 + 41 时间戳 + 5 数据中心 + 5 机器 + 12 序列号
 * <p>
 * 支持从生成的 ID 反解出各组成部分
 */
public class Snowflake implements Serializable, IdGenerator {

    private static final long serialVersionUID = 1L;

    /**
     * 默认起始时间戳 Thu, 04 Nov 2010 01:42:54 GMT
     */
    private static final long DEFAULT_TWEPOCH = 1288834974657L;

    /**
     * 默认允许时钟回拨毫秒数
     */
    private static final long DEFAULT_TIME_OFFSET = 2000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long DATA_CENTER_ID_BITS = 5L;
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
    private static final long SEQUENCE_BITS = 12L;
    private static final long TIMESTAMP_BITS = 41L;

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long twepoch;
    private final long workerId;
    private final long dataCenterId;
    private final boolean useSystemClock;
    private final long timeOffset;
    private final long randomSequenceLimit;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public Snowflake() {
        this(IdUtil.getWorkerId(IdUtil.getDataCenterId(MAX_DATA_CENTER_ID), MAX_WORKER_ID));
    }

    public Snowflake(long workerId) {
        this(workerId, IdUtil.getDataCenterId(MAX_DATA_CENTER_ID));
    }

    public Snowflake(long workerId, long dataCenterId) {
        this(workerId, dataCenterId, false);
    }

    public Snowflake(long workerId, long dataCenterId, boolean isUseSystemClock) {
        this(null, workerId, dataCenterId, isUseSystemClock);
    }

    public Snowflake(Date epochDate, long workerId, long dataCenterId, boolean isUseSystemClock) {
        this(epochDate, workerId, dataCenterId, isUseSystemClock, DEFAULT_TIME_OFFSET);
    }

    public Snowflake(Date epochDate, long workerId, long dataCenterId,
                     boolean isUseSystemClock, long timeOffset) {
        this(epochDate, workerId, dataCenterId, isUseSystemClock, timeOffset, 0);
    }

    /**
     * 全参构造：
     * 1. 设置起始时间戳（null 则使用默认值）
     * 2. 校验 workerId 和 dataCenterId 范围 [0, 31]
     * 3. 配置时钟回拨容忍、随机序列号上限
     */
    public Snowflake(Date epochDate, long workerId, long dataCenterId,
                     boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        this.twepoch = (null != epochDate) ? epochDate.getTime() : DEFAULT_TWEPOCH;
        this.workerId = Assert.checkBetween(workerId, 0, MAX_WORKER_ID);
        this.dataCenterId = Assert.checkBetween(dataCenterId, 0, MAX_DATA_CENTER_ID);
        this.useSystemClock = isUseSystemClock;
        this.timeOffset = timeOffset;
        this.randomSequenceLimit = Assert.checkBetween(randomSequenceLimit, 0, SEQUENCE_MASK);
    }

    /**
     * 从 ID 中提取 workerId
     */
    public long getWorkerId(long id) {
        return id >> WORKER_ID_SHIFT & ~(-1L << WORKER_ID_BITS);
    }

    /**
     * 从 ID 中提取 dataCenterId
     */
    public long getDataCenterId(long id) {
        return id >> DATA_CENTER_ID_SHIFT & ~(-1L << DATA_CENTER_ID_BITS);
    }

    /**
     * 从 ID 中提取生成时间戳
     */
    public long getGenerateDateTime(long id) {
        return (id >> TIMESTAMP_LEFT_SHIFT & ~(-1L << TIMESTAMP_BITS)) + twepoch;
    }

    /**
     * 生成下一个唯一 ID：
     * 1. 获取当前时间戳，容忍指定范围内的时钟回拨
     * 2. 同一毫秒内序列号自增，溢出则等待下一毫秒
     * 3. 不同毫秒时可选随机初始序列号（避免低频下全偶数）
     * 4. 按位组装返回
     */
    @Override
    public synchronized long nextId() {
        long timestamp = genTime();
        if (timestamp < this.lastTimestamp) {
            if (this.lastTimestamp - timestamp < timeOffset) {
                timestamp = lastTimestamp;
            } else {
                throw new IllegalStateException(StrUtil.format(
                        "Clock moved backwards. Refusing to generate id for {}ms",
                        lastTimestamp - timestamp));
            }
        }
        if (timestamp == this.lastTimestamp) {
            final long seq = (this.sequence + 1) & SEQUENCE_MASK;
            if (seq == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
            this.sequence = seq;
        } else {
            if (randomSequenceLimit > 1) {
                sequence = RandomUtil.randomLong(randomSequenceLimit);
            } else {
                sequence = 0L;
            }
        }
        lastTimestamp = timestamp;
        return ((timestamp - twepoch) << TIMESTAMP_LEFT_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    @Override
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    /**
     * 反解析雪花 ID 为各组成部分
     */
    public SnowflakeIdInfo parseSnowflakeId(long snowflakeId) {
        return SnowflakeIdInfo.builder()
                .sequence((int) (snowflakeId & ~(-1L << SEQUENCE_BITS)))
                .workerId((int) ((snowflakeId >> WORKER_ID_SHIFT) & ~(-1L << WORKER_ID_BITS)))
                .dataCenterId((int) ((snowflakeId >> DATA_CENTER_ID_SHIFT) & ~(-1L << DATA_CENTER_ID_BITS)))
                .timestamp((snowflakeId >> TIMESTAMP_LEFT_SHIFT) + twepoch)
                .build();
    }

    /**
     * 自旋等待下一毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = genTime();
        while (timestamp == lastTimestamp) {
            timestamp = genTime();
        }
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(StrUtil.format(
                    "Clock moved backwards. Refusing to generate id for {}ms",
                    lastTimestamp - timestamp));
        }
        return timestamp;
    }

    private long genTime() {
        return this.useSystemClock ? SystemClock.now() : System.currentTimeMillis();
    }
}
