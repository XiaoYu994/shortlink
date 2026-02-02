package com.xhy.shortlink.project.mq.producer.redis;

import com.xhy.shortlink.project.mq.event.ShortLinkExpireArchiveEvent;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkExpireArchiveRedisProducer implements ShortLinkMessageProducer<ShortLinkExpireArchiveEvent> {

    @Override
    public void send(ShortLinkExpireArchiveEvent event) {
        log.info("Redis 模式未启用过期归档延迟消息，跳过投递：{}", event.getFullShortUrl());
    }
}
