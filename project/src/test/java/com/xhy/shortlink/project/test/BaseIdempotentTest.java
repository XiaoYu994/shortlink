package com.xhy.shortlink.project.test;

import com.xhy.shortlink.project.handler.MessageQueueIdempotentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
public class BaseIdempotentTest {

    @Autowired
    protected MessageQueueIdempotentHandler idempotentHandler;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    // 清理 Redis key 的辅助方法
    protected void cleanRedis(String messageId) {
        stringRedisTemplate.delete("short-link:idempotent:" + messageId);
    }
}