package com.xhy.shortlink.project.mq.consumer.redis;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkCacheRedisConsumer{

    private final Cache<String, String> caffeineCache;

    public void onMessage(String fullShortUrl) {

        // 执行 Caffeine 本地缓存清除逻辑
        caffeineCache.invalidate(fullShortUrl);
        log.info("[Redis-PubSub] 广播清除本地缓存消息成功， FullShortUrl: {}",fullShortUrl);

    }
}
