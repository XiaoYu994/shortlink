package com.xhy.shortlink.framework.starter.database.handler;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Value;

/**
 * 自定义 ID 生成器
 * <p>
 * 实现 MyBatis-Plus 的 IdentifierGenerator 接口，
 * 替换默认的雪花算法实现，支持通过配置指定 workerId
 */
public class CustomIdGenerator implements IdentifierGenerator {

    @Value("${shortlink.snowflake.worker-id:1}")
    private long workerId;

    @Value("${shortlink.snowflake.datacenter-id:1}")
    private long datacenterId;

    private volatile Snowflake snowflake;

    /**
     * 生成全局唯一 ID：
     * 1. 延迟初始化 Snowflake 实例（双重检查锁）
     * 2. 调用 snowflake.nextId() 生成 64 位分布式唯一 ID
     *
     * @param entity 实体对象（未使用，保留接口兼容）
     * @return 雪花算法生成的唯一 ID
     */
    @Override
    public Number nextId(Object entity) {
        if (snowflake == null) {
            synchronized (this) {
                if (snowflake == null) {
                    snowflake = new Snowflake(workerId, datacenterId);
                }
            }
        }
        return snowflake.nextId();
    }

    /**
     * 精简版雪花算法实现
     * <p>
     * 64 位 ID 结构：1 位符号位 + 41 位时间戳 + 5 位数据中心 + 5 位机器 + 12 位序列号
     */
    private static class Snowflake {

        /** 起始时间戳 2024-01-01 00:00:00 */
        private static final long EPOCH = 1704067200000L;

        private static final long WORKER_ID_BITS = 5L;
        private static final long DATACENTER_ID_BITS = 5L;
        private static final long SEQUENCE_BITS = 12L;

        private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
        private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

        private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
        private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

        private final long workerId;
        private final long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        /**
         * 初始化雪花算法，校验 workerId 和 datacenterId 范围
         */
        Snowflake(long workerId, long datacenterId) {
            if (workerId > MAX_WORKER_ID || workerId < 0) {
                throw new IllegalArgumentException(
                        String.format("Worker ID must be between 0 and %d", MAX_WORKER_ID));
            }
            if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
                throw new IllegalArgumentException(
                        String.format("Datacenter ID must be between 0 and %d", MAX_DATACENTER_ID));
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        /**
         * 生成下一个唯一 ID：
         * 1. 获取当前时间戳，若发生时钟回拨则抛异常
         * 2. 同一毫秒内序列号自增，溢出则等待下一毫秒
         * 3. 不同毫秒则序列号归零
         * 4. 按位组装：时间戳 | 数据中心 | 机器 | 序列号
         */
        synchronized long nextId() {
            long timestamp = System.currentTimeMillis();
            if (timestamp < lastTimestamp) {
                throw new RuntimeException(String.format(
                        "Clock moved backwards. Refusing to generate id for %d milliseconds",
                        lastTimestamp - timestamp));
            }
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                    | (datacenterId << DATACENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        }

        /**
         * 自旋等待直到下一毫秒，确保时间戳单调递增
         */
        private long waitNextMillis(long lastTimestamp) {
            long timestamp = System.currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = System.currentTimeMillis();
            }
            return timestamp;
        }
    }
}
