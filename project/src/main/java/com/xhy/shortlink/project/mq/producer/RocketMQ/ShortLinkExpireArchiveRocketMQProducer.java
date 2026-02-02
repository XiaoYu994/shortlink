package com.xhy.shortlink.project.mq.producer.RocketMQ;

import com.xhy.shortlink.project.mq.base.BaseSendExtendDTO;
import com.xhy.shortlink.project.mq.event.ShortLinkExpireArchiveEvent;
import com.xhy.shortlink.project.mq.producer.AbstractCommonSendProduceTemplate;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.project.common.constant.RocketMQConstant.EXPIRE_ARCHIVE_TOPIC;

/**
 *  过期短链接生产者
 * @author XiaoYu
 */
@Component
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "RocketMQ")
public class ShortLinkExpireArchiveRocketMQProducer extends AbstractCommonSendProduceTemplate<ShortLinkExpireArchiveEvent>
        implements ShortLinkMessageProducer<ShortLinkExpireArchiveEvent> {

    public ShortLinkExpireArchiveRocketMQProducer(@Autowired RocketMQTemplate rocketMQTemplate) {
        super(rocketMQTemplate);
    }

    @Override
    public void send(ShortLinkExpireArchiveEvent event) {
        super.sendMessage(event);
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendDTO(ShortLinkExpireArchiveEvent messageEvent) {
        long delayTime = 0L;
        if (messageEvent.getExpireAt() != null) {
            delayTime = Math.max(0L, messageEvent.getExpireAt().getTime() - System.currentTimeMillis());
        }
        return BaseSendExtendDTO.builder()
                .eventName("过期短链归档")
                .topic(EXPIRE_ARCHIVE_TOPIC)
                .keys(messageEvent.getEventId())
                .delayTime(delayTime)
                .sendType(BaseSendExtendDTO.SendType.SYNC)
                .sentTimeout(2000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(ShortLinkExpireArchiveEvent messageEvent, BaseSendExtendDTO baseSendExtendDTO) {
        return MessageBuilder.withPayload(messageEvent)
                .setHeader(MessageConst.PROPERTY_KEYS, messageEvent.getFullShortUrl())
                .build();
    }
}
