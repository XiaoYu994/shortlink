package com.xhy.shortlink.project.mq.producer.redis;

import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.project.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.RISK_CHECK_STREAM_TOPIC_KEY;

/*
 * AI 风控检测消息 Redis 实现 (基于 Redis Stream)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkRiskRedisProducer implements ShortLinkMessageProducer<ShortLinkRiskEvent> {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void send(ShortLinkRiskEvent event) {
        try {

            // 1. 手动序列化为 JSON 字符串
            String jsonString = JSON.toJSONString(event);
            // 2. 封装为 MapRecord (Key="json", Value=JSON字符串)
            Map<String, String> payload = Collections.singletonMap("json", jsonString);
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .ofMap(payload)
                    .withStreamKey(RISK_CHECK_STREAM_TOPIC_KEY);
            // 3. 发送
            final RecordId recordId = stringRedisTemplate.opsForStream().add(record);
            // 相当于 onSuccess
             log.debug("[AI风控-Redis] 消息发送成功, RecordId: {}", recordId);

        } catch (Throwable e) {
            // 3. 相当于 onException
            log.error("[AI风控-Redis] 🚨 消息发送失败，执行补偿逻辑", e);

            // 执行补偿逻辑 (和 RocketMQ 保持一致)
            // handleCompensation(event);
        }
    }
}