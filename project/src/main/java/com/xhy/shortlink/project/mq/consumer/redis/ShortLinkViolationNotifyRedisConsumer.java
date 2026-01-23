package com.xhy.shortlink.project.mq.consumer.redis;

import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.project.dao.entity.UserNotificationDO;
import com.xhy.shortlink.project.mq.event.ShortLinkViolationEvent;
import com.xhy.shortlink.project.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Date;


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

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            String json = message.getValue().get("json");
            ShortLinkViolationEvent event = JSON.parseObject(json, ShortLinkViolationEvent.class);

            log.info("收到违规通知任务: {}", event.getFullShortUrl());


            // 2. 存入数据库 (站内信)
            UserNotificationDO notificationDO = UserNotificationDO.builder()
                    .userId(event.getUserId())
                    .type(1) // 1代表违规警告
                    .title("短链接封禁提醒")
                    .content(String.format("您的短链接 %s 因 [%s] 被系统检测为违规，现已封禁。如有异议请联系客服。",
                            event.getFullShortUrl(), event.getReason()))
                    .readFlag(0) // 未读
                    .createTime(new Date())
                    .build();

            notificationService.save(notificationDO);

            // 3. 发送短信 (模拟)
            // smsService.send(user.getPhone(), "您的链接已被封禁...");
            log.info(">> 已发送站内信通知用户");

            // 4. 发送邮件 (模拟)
            // emailService.send(user.getEmail(), "您的链接已被封禁...");

        } catch (Exception e) {
            log.error("处理违规通知失败", e);
        }
    }
}
