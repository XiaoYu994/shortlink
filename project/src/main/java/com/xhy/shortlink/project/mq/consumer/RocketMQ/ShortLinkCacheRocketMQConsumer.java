package com.xhy.shortlink.project.mq.consumer.RocketMQ;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;
import static com.xhy.shortlink.project.common.constant.RocketMQConstant.*;


/*
*  清楚本地缓存消费者，广播模式
* */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = CACHE_INVALIDATE_TOPIC,
        consumerGroup = CACHE_INVALIDATE_GROUP, // 消费者组
        selectorExpression = CACHE_INVALIDATE_TAG, // 指定只接收带有这个 Tag 的消息
        messageModel = MessageModel.BROADCASTING // 设置为广播模式
)
public class ShortLinkCacheRocketMQConsumer implements RocketMQListener<String> {

    private final Cache<String, String> caffeineCache;

    @Override
    public void onMessage(String fullShortUrl) {
        log.info("【MQ广播】接收到缓存清除消息，目标：{}", fullShortUrl);

        // 构造 Key (需要和 Service 中存入 Caffeine 的 Key 规则一致)
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);

        // 清除本地缓存
        caffeineCache.invalidate(key);
        caffeineCache.invalidate(keyIsNull);
    }
}