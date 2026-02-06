package com.xhy.shortlink.framework.starter.distributedid.handler;

import com.xhy.shortlink.framework.starter.distributedid.core.Snowflake;
import com.xhy.shortlink.framework.starter.distributedid.core.WorkIdChoose;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机 WorkId 选择策略
 * <p>
 * 通过 ThreadLocalRandom 随机生成 WorkId 和 DatacenterId，
 * 适合单机或测试环境，分布式环境下存在 ID 冲突风险
 */
@Slf4j
public class RandomWorkIdChoose implements WorkIdChoose {

    /**
     * 随机生成 WorkId，范围 [0, 31]
     */
    @Override
    public long chooseWorkId() {
        long workerId = ThreadLocalRandom.current().nextLong(Snowflake.MAX_WORKER_ID + 1);
        log.info("Random WorkId chosen: {}", workerId);
        return workerId;
    }

    /**
     * 随机生成 DatacenterId，范围 [0, 31]
     */
    @Override
    public long chooseDatacenterId() {
        long datacenterId = ThreadLocalRandom.current().nextLong(Snowflake.MAX_DATACENTER_ID + 1);
        log.info("Random DatacenterId chosen: {}", datacenterId);
        return datacenterId;
    }
}
