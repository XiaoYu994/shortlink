package com.xhy.shortlink.project.test;

import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.project.dto.resp.ShortLinkRiskCheckRespDTO;
import com.xhy.shortlink.project.mq.consumer.redis.ShortLinkRiskRedisConsumer;
import com.xhy.shortlink.project.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.project.service.ShortLinkService;
import com.xhy.shortlink.project.service.UrlRiskControlService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.RISK_CHECK_STREAM_TOPIC_KEY;
import static org.mockito.ArgumentMatchers.anyString;

public class RiskCheckIdempotentTest extends BaseIdempotentTest {

    @Autowired
    private ShortLinkRiskRedisConsumer riskConsumer;

    @MockBean
    private UrlRiskControlService riskControlService; // 🔥 Mock AI 服务

    @MockBean
    private ShortLinkService shortLinkService; // Mock 数据库查询

    @Test
    public void testAiCheckIdempotency() {
        String messageId = UUID.randomUUID().toString();
        cleanRedis(messageId);

        ShortLinkRiskEvent event = ShortLinkRiskEvent.builder()
                .eventId(messageId)
                .fullShortUrl("nurl.ink:8001/2L0u60")
                .originUrl("https://tenlajk.top")
                .build();

        // Mock 行为：假设 AI 返回“安全”
        Mockito.when(riskControlService.checkUrlRisk(anyString()))
                .thenReturn(new ShortLinkRiskCheckRespDTO(true, "NONE", "通过", "无风险"));
        // 1. 手动序列化为 JSON 字符串
        String jsonString = JSON.toJSONString(event);
        // 2. 封装为 MapRecord (Key="json", Value=JSON字符串)
        Map<String, String> payload = Collections.singletonMap("json", jsonString);
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .ofMap(payload)
                .withStreamKey(RISK_CHECK_STREAM_TOPIC_KEY)
                .withId(RecordId.of("1706087563000-0"));

        // --- 第一次调用 ---
        System.out.println(">>> 第 1 次调用 AI 风控");
        riskConsumer.onMessage(record);

        // --- 第二次调用 (模拟 MQ 重试) ---
        System.out.println(">>> 第 2 次调用 AI 风控 (应该被拦截)");
        riskConsumer.onMessage(record);

        // 🔍 核心验证：
        // 验证 riskControlService.checkUrlRisk 方法只被调用了 1 次
        // 如果没有幂等，这里会变成 2 次，意味着你花了 2 倍的钱调 AI
        Mockito.verify(riskControlService, Mockito.times(1)).checkUrlRisk(anyString());

        // 验证 Redis 标记为完成
        Assertions.assertTrue(idempotentHandler.isAccomplish(messageId));
        System.out.println("✅ AI 检测幂等测试通过");
    }
}