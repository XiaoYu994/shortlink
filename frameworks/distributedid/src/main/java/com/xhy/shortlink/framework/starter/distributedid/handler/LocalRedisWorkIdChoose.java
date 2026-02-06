package com.xhy.shortlink.framework.starter.distributedid.handler;

import com.xhy.shortlink.framework.starter.distributedid.core.Snowflake;
import com.xhy.shortlink.framework.starter.distributedid.core.WorkIdChoose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Collections;

/**
 * 基于 Redis 的 WorkId 分配策略
 * <p>
 * 通过 Lua 脚本原子递增 Redis 计数器分配 WorkId，
 * 对最大值取模确保范围合法。适合分布式环境，保证各节点 WorkId 唯一。
 * 若 Redis 不可用则自动降级到随机策略
 */
@Slf4j
@RequiredArgsConstructor
public class LocalRedisWorkIdChoose implements WorkIdChoose {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String WORKER_ID_KEY = "shortlink:distributed-id:snowflake:worker-id";
    private static final String DATACENTER_ID_KEY = "shortlink:distributed-id:snowflake:datacenter-id";

    private final RandomWorkIdChoose fallback = new RandomWorkIdChoose();

    /**
     * 通过 Redis Lua 脚本原子递增分配 WorkId：
     * 1. 执行 Lua 脚本对 key 做 INCR 操作
     * 2. 对 MAX_WORKER_ID+1 取模，确保范围 [0, 31]
     * 3. Redis 异常时降级到随机策略
     */
    @Override
    public long chooseWorkId() {
        try {
            long id = atomicIncrement(WORKER_ID_KEY);
            long workerId = id % (Snowflake.MAX_WORKER_ID + 1);
            log.info("Redis WorkId chosen: {}", workerId);
            return workerId;
        } catch (Exception e) {
            log.warn("Redis WorkId allocation failed, fallback to random", e);
            return fallback.chooseWorkId();
        }
    }

    /**
     * 通过 Redis Lua 脚本原子递增分配 DatacenterId：
     * 1. 执行 Lua 脚本对 key 做 INCR 操作
     * 2. 对 MAX_DATACENTER_ID+1 取模，确保范围 [0, 31]
     * 3. Redis 异常时降级到随机策略
     */
    @Override
    public long chooseDatacenterId() {
        try {
            long id = atomicIncrement(DATACENTER_ID_KEY);
            long datacenterId = id % (Snowflake.MAX_DATACENTER_ID + 1);
            log.info("Redis DatacenterId chosen: {}", datacenterId);
            return datacenterId;
        } catch (Exception e) {
            log.warn("Redis DatacenterId allocation failed, fallback to random", e);
            return fallback.chooseDatacenterId();
        }
    }

    /**
     * 执行 Lua 脚本原子递增指定 key 的值并返回
     */
    private long atomicIncrement(String key) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/work_id_increment.lua")));
        script.setResultType(Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key));
        if (result == null) {
            throw new RuntimeException("Lua script returned null for key: " + key);
        }
        return result;
    }
}
