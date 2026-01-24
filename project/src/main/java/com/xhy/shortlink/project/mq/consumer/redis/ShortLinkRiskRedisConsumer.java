package com.xhy.shortlink.project.mq.consumer.redis;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.resp.ShortLinkRiskCheckRespDTO;
import com.xhy.shortlink.project.handler.MessageQueueIdempotentHandler;
import com.xhy.shortlink.project.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.project.mq.event.ShortLinkViolationEvent;
import com.xhy.shortlink.project.service.ShortLinkService;
import com.xhy.shortlink.project.service.UrlRiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

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
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;


    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String streamId = message.getId().toString();
        final Map<String, String> proudcerMap = message.getValue();
        final ShortLinkRiskEvent event = JSON.parseObject(proudcerMap.get("json"), ShortLinkRiskEvent.class);
        String messageId = event.getEventId();
        if (messageQueueIdempotentHandler.isMessageBeingConsumed(messageId)) {
            // 判断当前的这个消息流程是否执行完成
            if (messageQueueIdempotentHandler.isAccomplish(messageId)) {
                return;
            }
            throw new ServiceException("消息未完成流程，需要消息队列重试");
        }
        try {

            log.info("[Redis] 开始对短链接进行 AI 风控审核: {}", event.getFullShortUrl());
            // 如果该链接已经被标记为“禁用/封禁”，说明之前的风控已经生效，直接跳过，别浪费 AI Token
            ShortLinkDO shortLinkDO = shortLinkService.getOne(Wrappers.<ShortLinkDO>lambdaQuery()
                    .eq(ShortLinkDO::getGid, event.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, event.getFullShortUrl())
                    .select(ShortLinkDO::getEnableStatus)); // 只查状态字段，效率高
            if (shortLinkDO != null && shortLinkDO.getEnableStatus() == LinkEnableStatusEnum.BANNED.getEnableStatus()) {
                log.info("⚠️ 该链接已被封禁，跳过 AI 检测: {}", event.getFullShortUrl());
                // 标记为完成，防止下次重复消费
                messageQueueIdempotentHandler.setAccomplish(messageId);
                return;
            }
            // 2. 调用 AI 进行检测
            ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(event.getOriginUrl());

            // 3. 如果 AI 判定为不安全
            if (!result.isSafe()) {
                log.warn("⚠️ [Redis] 发现违规链接！URL: {}, 原因: {}", event.getFullShortUrl(), result.getDetail());
                // 4. 封禁处理
                disableLink(event);
                // 5. 发送违规通知事件
                sendViolationNotification(event, result.getSummary());
            } else {
                log.info("✅ [Redis] AI 审核通过: {}", event.getFullShortUrl());
            }
            // 手动 ack
            stringRedisTemplate.opsForStream().acknowledge(RISK_CHECK_STREAM_GROUP_KEY, message);
            messageQueueIdempotentHandler.setAccomplish(messageId);
        } catch (Exception e) {
            // Redis Stream 消费异常如果不捕获，可能会导致线程池异常，建议捕获
            messageQueueIdempotentHandler.delMessageProcessed(messageId);
            log.error("[Redis] 风控消费异常, StreamId: {}", streamId, e);
            //  进阶思考：这里是否要 ACK？
            // 1. 如果你希望异常后【不重试】，在这里也加上 ACK。
            // 2. 如果你希望异常后【重试】，这里不要 ACK，然后需要写一个定时任务去处理 Pending List。
            // 简单做法：通常 AI 风控如果报错了（比如网络抖动），我们希望它能保留在 Pending List 里后续人工处理，所以这里不 ACK。
        }
    }

    /*
    *  发送风控通知
    * */
    private void sendViolationNotification(ShortLinkRiskEvent event, String reason) {
        try {
            ShortLinkViolationEvent violationEvent = ShortLinkViolationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .fullShortUrl(event.getFullShortUrl())
                    .gid(event.getGid())
                    .reason(reason)
                    .userId(event.getUserId())
                    .time(LocalDateTime.now())
                    .build();

            // 序列化发送
            String json = JSON.toJSONString(violationEvent);
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .ofMap(Collections.singletonMap("json", json))
                    .withStreamKey(NOTIFY_STREAM_TOPIC_KEY);

            stringRedisTemplate.opsForStream().add(record);
            log.info("已发送违规通知事件: {}", event.getFullShortUrl());

        } catch (Exception e) {
            log.error("发送违规通知失败", e);
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
            stringRedisTemplate.convertAndSend(CHANNEL_TOPIC_KEY, shortLinkRiskEvent.getFullShortUrl());
            log.info("[Redis] 已发送缓存清除广播: {}", shortLinkRiskEvent.getFullShortUrl());
        } catch (Exception e) {
            log.error("[Redis] 风控封禁广播发送失败", e);
        }
    }
}