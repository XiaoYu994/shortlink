package com.xhy.shortlink.framework.starter.distributedid.core;

import org.springframework.beans.factory.InitializingBean;

/**
 * 雪花算法 ID 生成器
 * <p>
 * 作为对外暴露的门面类，Bean 创建后通过 InitializingBean
 * 自动调用 WorkIdChoose 策略完成 Snowflake 初始化
 */
public class SnowflakeIdGenerator implements InitializingBean {

    private final WorkIdChoose workIdChoose;

    private Snowflake snowflake;

    public SnowflakeIdGenerator(WorkIdChoose workIdChoose) {
        this.workIdChoose = workIdChoose;
    }

    /**
     * Bean 初始化回调：
     * 1. 通过策略接口获取 workerId 和 datacenterId
     * 2. 构造 Snowflake 实例，完成初始化
     */
    @Override
    public void afterPropertiesSet() {
        long workerId = workIdChoose.chooseWorkId();
        long datacenterId = workIdChoose.chooseDatacenterId();
        this.snowflake = new Snowflake(workerId, datacenterId);
    }

    /**
     * 生成下一个分布式唯一 ID
     *
     * @return 64 位雪花算法 ID
     */
    public long nextId() {
        return snowflake.nextId();
    }
}
