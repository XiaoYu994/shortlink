package com.xhy.shortlink.project.mq.producer.RocketMQ;

import com.xhy.shortlink.project.mq.base.BaseSendExtendDTO;
import com.xhy.shortlink.project.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.project.mq.producer.AbstractCommonSendProduceTemplate;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.project.common.constant.RocketMQConstant.RISK_CHECK_TOPIC;

/*
* AI 风控检测异步消息
* */
@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "RocketMQ")
public class ShortLinkRiskRocketMQProducer extends AbstractCommonSendProduceTemplate<ShortLinkRiskEvent> implements ShortLinkMessageProducer<ShortLinkRiskEvent> {

    public ShortLinkRiskRocketMQProducer(@Autowired RocketMQTemplate rocketMQTemplate) {
        super(rocketMQTemplate);
    }


    @Override
    public void send(ShortLinkRiskEvent event) {
        super.sendMessage(event, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                // 发送成功，静默处理
            }

            @Override
            public void onException(Throwable e) {
                log.error("[AI风控] 🚨 消息发送失败，执行补偿逻辑", e);
                // 3. 🔥 特殊业务逻辑：发送都失败了，直接把数据库状态改为“审核异常”
                // shortLinkService.updateStatus(event.getGid(), event.getFullShortUrl(), "RISK_SEND_FAIL");
                // 或者：发送告警邮件给管理员
                // alertService.sendAlert("风控消息发送挂了！");
            }
        });
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendDTO(ShortLinkRiskEvent messageEvent) {
        return BaseSendExtendDTO.builder()
                .eventName("AI风控检测")
                .topic(RISK_CHECK_TOPIC)
                .keys(messageEvent.getFullShortUrl())
                .sendType(BaseSendExtendDTO.SendType.ASYNC) // 风控必须异步，不能卡用户创建
                .sentTimeout(2000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(ShortLinkRiskEvent messageEvent, BaseSendExtendDTO baseSendExtendDTO) {
        return MessageBuilder.withPayload(messageEvent)
                .setHeader(MessageConst.PROPERTY_KEYS,messageEvent.getFullShortUrl())
                .build();
    }

}