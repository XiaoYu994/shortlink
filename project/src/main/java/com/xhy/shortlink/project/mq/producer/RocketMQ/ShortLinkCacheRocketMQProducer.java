package com.xhy.shortlink.project.mq.producer.RocketMQ;

import com.xhy.shortlink.project.mq.base.BaseSendExtendDTO;
import com.xhy.shortlink.project.mq.producer.AbstractCommonSendProduceTemplate;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.project.common.constant.RocketMQConstant.CACHE_INVALIDATE_TAG;
import static com.xhy.shortlink.project.common.constant.RocketMQConstant.CACHE_INVALIDATE_TOPIC;

/*
*  发送 MQ 广播消息通知清除本地 Caffeine
* */
@Component
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "RocketMQ")
public class ShortLinkCacheRocketMQProducer extends AbstractCommonSendProduceTemplate<String> implements ShortLinkMessageProducer<String> {

    public ShortLinkCacheRocketMQProducer(@Autowired RocketMQTemplate rocketMQTemplate) {
        super(rocketMQTemplate);
    }

    @Override
    public void send(String event) {
        super.sendMessage(event);
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendDTO(String fullShortUrl) {
        return BaseSendExtendDTO.builder()
                .eventName("缓存清除广播")
                .topic(CACHE_INVALIDATE_TOPIC)
                .tag(CACHE_INVALIDATE_TAG)
                // ONEWAY: 极致性能，允许极低概率丢失
                // ASYNC: 性能好，有回调记录日志
                .sendType(BaseSendExtendDTO.SendType.ONEWAY)
                .keys(fullShortUrl)
                .sentTimeout(2000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(String messageEvent, BaseSendExtendDTO baseSendExtendDTO) {
        return MessageBuilder.withPayload(messageEvent)
                .setHeader(MessageConst.PROPERTY_KEYS, baseSendExtendDTO.getKeys())
                .setHeader(MessageConst.PROPERTY_TAGS, baseSendExtendDTO.getTag())
                .build();
    }

}
