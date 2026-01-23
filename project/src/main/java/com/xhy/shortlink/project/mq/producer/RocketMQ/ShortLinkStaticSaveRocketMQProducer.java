package com.xhy.shortlink.project.mq.producer.RocketMQ;


import com.xhy.shortlink.project.mq.base.BaseSendExtendDTO;
import com.xhy.shortlink.project.mq.event.ShortLinkStatsRecordEvent;
import com.xhy.shortlink.project.mq.producer.AbstractCommonSendProduceTemplate;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.project.common.constant.RocketMQConstant.STATIC_TOPIC;

/**
 * 短链接监控状态保存消息队列生产者 RocketMQ实现方式
 */
@Component
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "RocketMQ")
public class ShortLinkStaticSaveRocketMQProducer extends AbstractCommonSendProduceTemplate<ShortLinkStatsRecordEvent> implements ShortLinkMessageProducer<ShortLinkStatsRecordEvent> {

    // 构造注入 RocketMQTemplate
    public ShortLinkStaticSaveRocketMQProducer(@Autowired RocketMQTemplate rocketMQTemplate) {
        super(rocketMQTemplate);
    }

    // 接口业务
    @Override
    public void send(ShortLinkStatsRecordEvent event) {
        // 调用父类的通用逻辑
        super.sendMessage(event);
    }

    // 2. 实现父类抽象方法：构建 RocketMQ 特有的参数
    @Override
    protected BaseSendExtendDTO buildBaseSendExtendDTO(ShortLinkStatsRecordEvent messageEvent) {
        return BaseSendExtendDTO.builder()
                .eventName("短链接统计消息")
                .topic(STATIC_TOPIC)
                .sendType(BaseSendExtendDTO.SendType.SYNC) // 同步发送消息
                .keys(messageEvent.getFullShortUrl())
                .sentTimeout(2000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(ShortLinkStatsRecordEvent messageEvent, BaseSendExtendDTO baseSendExtendDTO) {
        // 构建 Spring Message
        return MessageBuilder.withPayload(messageEvent)
                .setHeader(MessageConst.PROPERTY_KEYS, baseSendExtendDTO.getKeys())
                .setHeader(MessageConst.PROPERTY_TAGS, baseSendExtendDTO.getTag())
                .build();
    }
}
