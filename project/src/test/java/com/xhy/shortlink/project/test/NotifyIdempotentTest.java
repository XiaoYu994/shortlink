package com.xhy.shortlink.project.test;

import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.project.dao.entity.UserNotificationDO;
import com.xhy.shortlink.project.mq.consumer.redis.ShortLinkViolationNotifyRedisConsumer;
import com.xhy.shortlink.project.mq.event.ShortLinkViolationEvent;
import com.xhy.shortlink.project.service.UserNotificationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.NOTIFY_STREAM_TOPIC_KEY;

public class NotifyIdempotentTest extends BaseIdempotentTest {

    @Autowired
    private ShortLinkViolationNotifyRedisConsumer notifyConsumer;

    @MockBean
    private UserNotificationService notificationService; // 🔥 Mock 通知服务

    @Test
    public void testNotifySaveIdempotency() {
        String messageId = UUID.randomUUID().toString();
        cleanRedis(messageId);

        ShortLinkViolationEvent event = ShortLinkViolationEvent.builder()
                .eventId(messageId) // 注意：这里用 eventId 作为 messageId
                .fullShortUrl("nurl.ink/notifyTest")
                .userId(1L)
                .reason("测试违规")
                .build();
        // 1. 手动序列化为 JSON 字符串
        String jsonString = JSON.toJSONString(event);
        // 2. 封装为 MapRecord (Key="json", Value=JSON字符串)
        Map<String, String> payload = Collections.singletonMap("json", jsonString);
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .ofMap(payload)
                .withStreamKey(NOTIFY_STREAM_TOPIC_KEY);

        // --- 第一次调用 ---
        System.out.println(">>> 第 1 次发送通知");
        notifyConsumer.onMessage(record);

        // --- 第二次调用 ---
        System.out.println(">>> 第 2 次发送通知 (应该被拦截)");
        notifyConsumer.onMessage(record);

        // 🔍 核心验证：
        // 验证数据库 Save 方法只被调用了 1 次
        Mockito.verify(notificationService, Mockito.times(1)).save(Mockito.any(UserNotificationDO.class));

        Assertions.assertTrue(idempotentHandler.isAccomplish(messageId));
        System.out.println("✅ 通知发送幂等测试通过");
    }
}