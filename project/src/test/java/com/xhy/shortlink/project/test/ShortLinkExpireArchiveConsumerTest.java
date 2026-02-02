package com.xhy.shortlink.project.test;

import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToHistoryDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkHistoryDO;
import com.xhy.shortlink.project.dao.mapper.*;
import com.xhy.shortlink.project.handler.MessageQueueIdempotentHandler;
import com.xhy.shortlink.project.mq.consumer.RocketMQ.ShortLinkExpireArchiveRocketMQConsumer;
import com.xhy.shortlink.project.mq.event.ShortLinkExpireArchiveEvent;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import com.xhy.shortlink.project.service.UserNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;


/*
*  验证当收到“短链接已过期并需要归档”的 MQ 消息时，系统能否将该链接从热库迁移到历史库。
* */
public class ShortLinkExpireArchiveConsumerTest {

    @Test
    public void testArchiveExpiredHotLink() {
        ShortLinkMapper shortLinkMapper = Mockito.mock(ShortLinkMapper.class);
        ShortLinkGoToMapper shortLinkGoToMapper = Mockito.mock(ShortLinkGoToMapper.class);
        ShortLinkColdMapper shortLinkColdMapper = Mockito.mock(ShortLinkColdMapper.class);
        ShortLinkGoToColdMapper shortLinkGoToColdMapper = Mockito.mock(ShortLinkGoToColdMapper.class);
        ShortLinkHistoryMapper shortLinkHistoryMapper = Mockito.mock(ShortLinkHistoryMapper.class);
        ShortLinkGoToHistoryMapper shortLinkGoToHistoryMapper = Mockito.mock(ShortLinkGoToHistoryMapper.class);
        MessageQueueIdempotentHandler idempotentHandler = Mockito.mock(MessageQueueIdempotentHandler.class);
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ShortLinkMessageProducer<String> cacheProducer = Mockito.mock(ShortLinkMessageProducer.class);
        @SuppressWarnings("unchecked")
        ShortLinkMessageProducer<ShortLinkExpireArchiveEvent> expireArchiveProducer = Mockito.mock(ShortLinkMessageProducer.class);
        UserNotificationService userNotificationService = Mockito.mock(UserNotificationService.class);

        ShortLinkExpireArchiveRocketMQConsumer consumer = new ShortLinkExpireArchiveRocketMQConsumer(
                shortLinkMapper,
                shortLinkGoToMapper,
                shortLinkColdMapper,
                shortLinkGoToColdMapper,
                shortLinkHistoryMapper,
                shortLinkGoToHistoryMapper,
                idempotentHandler,
                stringRedisTemplate,
                cacheProducer,
                expireArchiveProducer,
                userNotificationService
        );

        ShortLinkDO hotLink = new ShortLinkDO();
        hotLink.setGid("g1");
        hotLink.setFullShortUrl("nurl.ink/expired");
        hotLink.setEnableStatus(3);

        ShortLinkGoToDO gotoDO = new ShortLinkGoToDO();
        gotoDO.setGid("g1");
        gotoDO.setFullShortUrl("nurl.ink/expired");

        Mockito.when(idempotentHandler.isMessageBeingConsumed(any())).thenReturn(false);
        Mockito.when(shortLinkMapper.selectOne(any())).thenReturn(hotLink);
        Mockito.when(shortLinkGoToMapper.selectOne(any())).thenReturn(gotoDO);

        ShortLinkExpireArchiveEvent event = ShortLinkExpireArchiveEvent.builder()
                .eventId("e1")
                .gid("g1")
                .fullShortUrl("nurl.ink/expired")
                .expireAt(new Date())
                .stage(ShortLinkExpireArchiveEvent.Stage.ARCHIVE)
                .build();

        consumer.onMessage(event);

        Mockito.verify(shortLinkHistoryMapper, Mockito.times(1)).insert((ShortLinkHistoryDO) any());
        Mockito.verify(shortLinkGoToHistoryMapper, Mockito.times(1)).insert((ShortLinkGoToHistoryDO) any());
        Mockito.verify(shortLinkMapper, Mockito.times(1)).deletePhysical(eq("g1"), eq("nurl.ink/expired"));
        Mockito.verify(cacheProducer, Mockito.times(1)).send(eq("nurl.ink/expired"));
    }
}
