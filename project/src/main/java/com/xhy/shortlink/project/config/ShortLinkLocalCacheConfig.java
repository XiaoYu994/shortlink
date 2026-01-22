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
                .maximumSize(5000) // 防止内存溢出
                .expireAfterWrite(30, TimeUnit.SECONDS) // 写入30秒后过期，保证最终一致性
                .build();
    }
}
