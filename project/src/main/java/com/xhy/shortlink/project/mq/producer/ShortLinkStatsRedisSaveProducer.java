package com.xhy.shortlink.project.mq.producer;


import com.xhy.shortlink.project.mq.ShortLinkStatsMessageProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/*
 * 短链接监控状态保存消息队列生产者 Redis steam 实现
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkStatsRedisSaveProducer implements ShortLinkStatsMessageProducer {


    private final StringRedisTemplate stringRedisTemplate;


    /*
     * 发送延迟消费短链接统计
     */
    public void send(Map<String, String> producerMap) {
        stringRedisTemplate.opsForStream().add(SHORT_LINK_STATS_STREAM_TOPIC_KEY, producerMap);
    }
}
