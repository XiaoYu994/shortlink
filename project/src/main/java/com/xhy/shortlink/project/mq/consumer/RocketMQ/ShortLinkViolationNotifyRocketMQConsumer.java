package com.xhy.shortlink.project.mq.consumer.RocketMQ;


import com.xhy.shortlink.project.dao.entity.UserNotificationDO;
import com.xhy.shortlink.project.mq.event.ShortLinkViolationEvent;
import com.xhy.shortlink.project.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Date;

import static com.xhy.shortlink.project.common.constant.RocketMQConstant.NOTIFY_GROUP;
import static com.xhy.shortlink.project.common.constant.RocketMQConstant.NOTIFY_TOPIC;

/*
 * RocketMQ 消费者：处理违规通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = NOTIFY_TOPIC,
        consumerGroup = NOTIFY_GROUP
)
public class ShortLinkViolationNotifyRocketMQConsumer implements RocketMQListener<ShortLinkViolationEvent> {
    private final UserNotificationService notificationService;

    @Override
    public void onMessage(ShortLinkViolationEvent event) {
        log.info("[RocketMQ] 收到违规通知任务: {}", event.getFullShortUrl());

        try {
            // 1. 业务逻辑：存入数据库 (站内信)
            UserNotificationDO notificationDO = UserNotificationDO.builder()
                    .userId(event.getUserId())
                    .type(1)    // 1-违规警告
                    .title("短链接封禁提醒")
                    .content(String.format("您的短链接 %s 因 [%s] 被系统检测为违规，现已封禁。",
                            event.getFullShortUrl(), event.getReason()))
                    .readFlag(0)
                    .createTime(new Date())
                    .build();

            notificationService.save(notificationDO);
            log.info(">> 已生成站内信通知");

            // 2. 业务逻辑：发送短信/邮件
            // smsService.send(...);

        } catch (Exception e) {
            log.error("[RocketMQ] 处理通知消息失败", e);
            // RocketMQ 默认失败会重试，如果不想重试可以 try-catch 不抛出异常
        }
    }
}
