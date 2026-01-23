package com.xhy.shortlink.project.config;

import com.xhy.shortlink.project.mq.consumer.redis.ShortLinkCacheRedisConsumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/*
*  MessageListener 发布订阅模式
* */
@Configuration
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class RedisPubSubConfiguration {

    /**
     * 配置消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                                   MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 订阅 "short-link:cache-invalidate:topic" 频道
        container.addMessageListener(listenerAdapter, new PatternTopic("short-link:cache-invalidate:topic"));
        return container;
    }

    /**
     * 绑定消息监听者（你的消费者逻辑类）
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(ShortLinkCacheRedisConsumer consumer) {
        // 指定调用 consumer 对象的 onMessage 方法
        return new MessageListenerAdapter(consumer, "onMessage");
    }
}
