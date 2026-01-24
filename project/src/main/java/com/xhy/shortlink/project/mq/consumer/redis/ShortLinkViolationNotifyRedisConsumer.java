package com.xhy.shortlink.project.mq.consumer.redis;

import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.dao.entity.UserNotificationDO;
import com.xhy.shortlink.project.handler.MessageQueueIdempotentHandler;
import com.xhy.shortlink.project.mq.event.ShortLinkViolationEvent;
import com.xhy.shortlink.project.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;


/*
*  通知消费者 (处理通知)
* */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkViolationNotifyRedisConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    private final UserNotificationService notificationService;
    // private final SmsService smsService; // 假设你有短信服务
    // private final EmailService emailService; // 假设你有邮件服务
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;
    private final StringRedisTemplate stringRedisTemplate;


    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String json = message.getValue().get("json");
        ShortLinkViolationEvent event = JSON.parseObject(json, ShortLinkViolationEvent.class);
        RecordId id = message.getId();
        String messageId = event.getEventId();
        String stream = message.getStream(); // Stream 的 Key
        try {
            // 如果被消费
            if(messageQueueIdempotentHandler.isMessageBeingConsumed(messageId)) {
                // 消息执行完成
                if(messageQueueIdempotentHandler.isAccomplish(messageId)) {
                    return;
                }
                throw new ServiceException("消息未完成流程，需要消息队列重试");
            }

            log.info("收到违规通知任务 Redis: {}", event.getFullShortUrl());


            // 2. 存入数据库 (站内信)
            try {
                UserNotificationDO notificationDO = UserNotificationDO.builder()
                        .eventId(event.getEventId())
                        .userId(event.getUserId())
                        .type(1) // 1代表违规警告
                        .title("短链接封禁提醒")
                        .eventId(event.getEventId())
                        .content(String.format("您的短链接 %s 因 [%s] 被系统检测为违规，现已封禁。如有异议请联系客服。",
                                event.getFullShortUrl(), event.getReason()))
                        .readFlag(0) // 未读
                        .createTime(new Date())
                        .build();

                notificationService.save(notificationDO);
            } catch (DuplicateKeyException e) {
                // 捕获唯一索引冲突异常
                log.warn("数据库已存在该通知，忽略入库。eventId: {}", event.getEventId());
                // 这里不需要抛出异常，否则 MQ 会以为消费失败又重试
            }

            // 3. 发送短信 (模拟)
            // smsService.send(user.getPhone(), "您的链接已被封禁...");
            log.info(">> 已发送站内信通知用户");

            // 4. 发送邮件 (模拟)
            // emailService.send(user.getEmail(), "您的链接已被封禁...");

            // 处理成功后，从 Redis Stream 中删除该消息
            stringRedisTemplate.opsForStream().delete(Objects.requireNonNull(stream), id.getValue());
        } catch (Exception e) {
            messageQueueIdempotentHandler.delMessageProcessed(messageId);
            log.error("处理违规通知失败", e);
            throw e;
        }
        messageQueueIdempotentHandler.setAccomplish(messageId);

    }
}
