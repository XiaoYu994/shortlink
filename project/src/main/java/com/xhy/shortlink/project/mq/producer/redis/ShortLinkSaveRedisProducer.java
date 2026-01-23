package com.xhy.shortlink.project.mq.producer.redis;


import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.project.mq.event.ShortLinkStatsRecordEvent;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/*
 * 短链接监控状态保存消息队列生产者 Redis steam 实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkSaveRedisProducer implements ShortLinkMessageProducer<ShortLinkStatsRecordEvent> {


    private final StringRedisTemplate stringRedisTemplate;

    /*
     * 发送延迟消费短链接统计
     */
    public void send(ShortLinkStatsRecordEvent event) {
        try {
            // 1. 手动序列化为 JSON 字符串
            String jsonString = JSON.toJSONString(event);
            // 2. 封装为 MapRecord (Key="json", Value=JSON字符串)
            Map<String, String> payload = Collections.singletonMap("json", jsonString);
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .ofMap(payload)
                    .withStreamKey(SHORT_LINK_STATS_STREAM_TOPIC_KEY);
            // 3. 发送
            stringRedisTemplate.opsForStream().add(record);
            log.info("[Redis-Stream] 发送监控统计消息 ,{}", record);
        } catch (Throwable e) {
            log.error("[Redis-Stream] 发送监控统计消息失败", e);
        }
    }
}
