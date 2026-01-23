package com.xhy.shortlink.project.mq.consumer.redis;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.resp.ShortLinkRiskCheckRespDTO;
import com.xhy.shortlink.project.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.project.service.ShortLinkService;
import com.xhy.shortlink.project.service.UrlRiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
// 只有当配置选择 Redis 时才加载此 Bean
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkRiskRedisConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final UrlRiskControlService riskControlService;
    private final ShortLinkService shortLinkService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String streamId = message.getId().toString();
        try {
            final Map<String, String> proudcerMap = message.getValue();
            final ShortLinkRiskEvent shortLinkRiskEvent = JSON.parseObject(proudcerMap.get("json"), ShortLinkRiskEvent.class);
            log.info("[Redis] 开始对短链接进行 AI 风控审核: {}", shortLinkRiskEvent.getFullShortUrl());

            // 2. 调用 AI 进行检测
            ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(shortLinkRiskEvent.getOriginUrl());

            // 3. 如果 AI 判定为不安全
            if (!result.isSafe()) {
                log.warn("⚠️ [Redis] 发现违规链接！URL: {}, 原因: {}", shortLinkRiskEvent.getFullShortUrl(), result.getDescription());
                // 4. 封禁处理
                disableLink(shortLinkRiskEvent);
            } else {
                log.info("✅ [Redis] AI 审核通过: {}", shortLinkRiskEvent.getFullShortUrl());
            }
            // 手动 ack
            stringRedisTemplate.opsForStream().acknowledge(RISK_CHECK_STREAM_GROUP_KEY, message);
        } catch (Exception e) {
            // Redis Stream 消费异常如果不捕获，可能会导致线程池异常，建议捕获
            log.error("[Redis] 风控消费异常, StreamId: {}", streamId, e);
            //  进阶思考：这里是否要 ACK？
            // 1. 如果你希望异常后【不重试】，在这里也加上 ACK。
            // 2. 如果你希望异常后【重试】，这里不要 ACK，然后需要写一个定时任务去处理 Pending List。
            // 简单做法：通常 AI 风控如果报错了（比如网络抖动），我们希望它能保留在 Pending List 里后续人工处理，所以这里不 ACK。
        }
    }

    private void disableLink(ShortLinkRiskEvent shortLinkRiskEvent) {
        // A. 修改数据库状态 enable_status = 1 (禁用)
        shortLinkService.update(null, Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, shortLinkRiskEvent.getGid())
                .eq(ShortLinkDO::getFullShortUrl, shortLinkRiskEvent.getFullShortUrl())
                .set(ShortLinkDO::getEnableStatus, 1));

        // B. 删除 Redis 字符串缓存 (L2)
        String redisKey = String.format(GOTO_SHORT_LINK_KEY, shortLinkRiskEvent.getFullShortUrl());
        stringRedisTemplate.delete(redisKey);

        // C. 发送 Redis Pub/Sub 广播消息，清除所有节点的本地 Caffeine 缓存 (L1)
        try {
            // 注意：这里使用的是 Redis 的 convertAndSend，不是 RocketMQ
            stringRedisTemplate.convertAndSend(CHANNEL_TOPIC, shortLinkRiskEvent.getFullShortUrl());
            log.info("[Redis] 已发送缓存清除广播: {}", shortLinkRiskEvent.getFullShortUrl());
        } catch (Exception e) {
            log.error("[Redis] 风控封禁广播发送失败", e);
        }
    }
}