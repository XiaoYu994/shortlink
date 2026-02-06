package com.xhy.shortlink.framework.starter.distributedid.config;

import com.xhy.shortlink.framework.starter.distributedid.core.SnowflakeIdGenerator;
import com.xhy.shortlink.framework.starter.distributedid.core.WorkIdChoose;
import com.xhy.shortlink.framework.starter.distributedid.handler.LocalRedisWorkIdChoose;
import com.xhy.shortlink.framework.starter.distributedid.handler.RandomWorkIdChoose;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 分布式 ID 自动装配配置
 * <p>
 * 装配策略：
 * 1. 若容器中存在 StringRedisTemplate，注册 LocalRedisWorkIdChoose（Redis 策略）
 * 2. 否则注册 RandomWorkIdChoose（随机策略）作为兜底
 * 3. 基于选定的 WorkIdChoose 构建 SnowflakeIdGenerator
 */
@Configuration
public class DistributedIdAutoConfiguration {

    /**
     * Redis 可用时注册 Redis WorkId 分配策略，优先级高于随机策略
     */
    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    public WorkIdChoose localRedisWorkIdChoose(StringRedisTemplate stringRedisTemplate) {
        return new LocalRedisWorkIdChoose(stringRedisTemplate);
    }

    /**
     * 兜底策略：无 Redis 时使用随机分配
     */
    @Bean
    @ConditionalOnMissingBean
    public WorkIdChoose randomWorkIdChoose() {
        return new RandomWorkIdChoose();
    }

    /**
     * 注册雪花算法 ID 生成器，通过 InitializingBean 自动完成初始化
     */
    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator(WorkIdChoose workIdChoose) {
        return new SnowflakeIdGenerator(workIdChoose);
    }
}
