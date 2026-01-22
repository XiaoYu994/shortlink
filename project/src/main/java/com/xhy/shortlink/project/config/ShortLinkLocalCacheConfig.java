package com.xhy.shortlink.project.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/*
* caffeine 本地缓存配置类
* */
@Configuration
public class ShortLinkLocalCacheConfig {

    @Bean
    public Cache<String, String> shortLinkCache() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(10000) // 存1万条热点数据，大概占用 10MB-20MB 内存，很安全
                .expireAfterWrite(30, TimeUnit.MINUTES) // 写入半小时后过期，避免频繁失效导致穿透到 Redis
                .recordStats() // 开启统计，生产环境可以通过监控看到缓存命中率
                .build();
    }
}
