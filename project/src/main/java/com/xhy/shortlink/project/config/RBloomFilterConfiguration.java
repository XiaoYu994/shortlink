package com.xhy.shortlink.project.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 布隆过滤器配置
 */
@Configuration
public class RBloomFilterConfiguration {

    /**
     * 防止短链接生成重复的布隆过滤器
     */
    @Bean
    public RBloomFilter<String> shortlinkUriCreateCachePenetrationBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> cachePenetrationBloomFilter = redissonClient.getBloomFilter("shortlinkUriCreateCachePenetrationBloomFilter");

        /*
        * tryInit 有两个核心参数：
        ● expectedInsertions：预估布隆过滤器存储的元素长度。
        ● falseProbability：运行的误判率。
        * */
        cachePenetrationBloomFilter.tryInit(100000000, 0.001);
        return cachePenetrationBloomFilter;
    }
}
