package com.xhy.shortlink.framework.starter.distributedid.core;

/**
 * WorkId 选择策略接口
 * <p>
 * 定义 WorkId 和 DatacenterId 的获取方式，
 * 不同实现对应不同的分配策略（随机、Redis 等）
 */
public interface WorkIdChoose {

    /**
     * 选择 WorkId，返回值范围 [0, 31]
     *
     * @return workerId
     */
    long chooseWorkId();

    /**
     * 选择 DatacenterId，返回值范围 [0, 31]
     *
     * @return datacenterId
     */
    long chooseDatacenterId();
}
