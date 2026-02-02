package com.xhy.shortlink.project.mq.consumer.RocketMQ;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.project.common.enums.ValidDateTypeEnum;
import com.xhy.shortlink.project.dao.entity.*;
import com.xhy.shortlink.project.dao.mapper.*;
import com.xhy.shortlink.project.handler.MessageQueueIdempotentHandler;
import com.xhy.shortlink.project.mq.event.ShortLinkExpireArchiveEvent;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import com.xhy.shortlink.project.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;
import static com.xhy.shortlink.project.common.constant.RocketMQConstant.EXPIRE_ARCHIVE_GROUP;
import static com.xhy.shortlink.project.common.constant.RocketMQConstant.EXPIRE_ARCHIVE_TOPIC;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "RocketMQ")
@RocketMQMessageListener(
        topic = EXPIRE_ARCHIVE_TOPIC,
        consumerGroup = EXPIRE_ARCHIVE_GROUP
)
public class ShortLinkExpireArchiveRocketMQConsumer implements RocketMQListener<ShortLinkExpireArchiveEvent> {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final ShortLinkHistoryMapper shortLinkHistoryMapper;
    private final ShortLinkGoToHistoryMapper shortLinkGoToHistoryMapper;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;
    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMessageProducer<String> cacheProducer;
    private final ShortLinkMessageProducer<ShortLinkExpireArchiveEvent> expireArchiveProducer;
    private final UserNotificationService userNotificationService;

    @Value("${short-link.expire.grace-days:30}")
    private int graceDays;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ShortLinkExpireArchiveEvent event) {
        String messageId = event.getEventId();
        if (messageQueueIdempotentHandler.isMessageBeingConsumed(messageId)) {
            if (messageQueueIdempotentHandler.isAccomplish(messageId)) {
                return;
            }
            throw new ServiceException("消息未完成流程，需要消息队列重试");
        }
        try {
            handleExpire(event);
        } catch (Throwable ex) {
            messageQueueIdempotentHandler.delMessageProcessed(messageId);
            log.error("过期短链归档消费异常", ex);
            throw ex;
        }
        messageQueueIdempotentHandler.setAccomplish(messageId);
    }

    /**
     * 过期处理入口：先冻结，再归档（冻结期到期后）
     */
    private void handleExpire(ShortLinkExpireArchiveEvent event) {
        ShortLinkExpireArchiveEvent.Stage stage = event.getStage();
        if (stage == null) {
            stage = ShortLinkExpireArchiveEvent.Stage.FREEZE;
        }
        if (stage == ShortLinkExpireArchiveEvent.Stage.FREEZE) {
            handleFreeze(event);
            return;
        }
        handleArchive(event);
    }

    /**
     * 到期进入冻结期：禁用短链、通知用户、投递冻结期结束归档消息
     */
    private void handleFreeze(ShortLinkExpireArchiveEvent event) {
        String fullShortUrl = event.getFullShortUrl();
        String gid = event.getGid();
        Date now = new Date();

        ShortLinkDO hotLink = shortLinkMapper.selectOne(Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, gid)
                .eq(ShortLinkDO::getFullShortUrl, fullShortUrl));
        if (hotLink != null) {
            if (!isExpired(hotLink, now)) {
                return;
            }
            if (hotLink.getEnableStatus() != null && hotLink.getEnableStatus() != LinkEnableStatusEnum.ENABLE.getEnableStatus()) {
                return;
            }
            shortLinkMapper.update(null, Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, gid)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .set(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.FROZEN.getEnableStatus()));
            sendExpireNotify(event);
            scheduleArchive(event);
            clearCache(fullShortUrl);
            return;
        }

        ShortLinkColdDO coldLink = shortLinkColdMapper.selectOne(Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, gid)
                .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl));
        if (coldLink == null) {
            return;
        }
        if (!isExpired(coldLink, now)) {
            return;
        }
        if (coldLink.getEnableStatus() != null && coldLink.getEnableStatus() != LinkEnableStatusEnum.ENABLE.getEnableStatus()) {
            return;
        }
        shortLinkColdMapper.update(null, Wrappers.lambdaUpdate(ShortLinkColdDO.class)
                .eq(ShortLinkColdDO::getGid, gid)
                .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl)
                .set(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.FROZEN.getEnableStatus()));
        sendExpireNotify(event);
        scheduleArchive(event);
        clearCache(fullShortUrl);
    }

    /**
     * 冻结期结束后归档消息投递
     */
    private void scheduleArchive(ShortLinkExpireArchiveEvent event) {
        Date archiveAt = new Date(System.currentTimeMillis() + (long) graceDays * 24 * 60 * 60 * 1000);
        expireArchiveProducer.send(ShortLinkExpireArchiveEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .gid(event.getGid())
                .fullShortUrl(event.getFullShortUrl())
                .expireAt(archiveAt)
                .userId(event.getUserId())
                .stage(ShortLinkExpireArchiveEvent.Stage.ARCHIVE)
                .build());
    }

    /**
     * 发送冻结期通知（仅一次）
     */
    private void sendExpireNotify(ShortLinkExpireArchiveEvent event) {
        if (event.getUserId() == null) {
            return;
        }
        UserNotificationDO notification = UserNotificationDO.builder()
                .userId(event.getUserId())
                .type(0)
                .title("短链已过期")
                .content("短链已到期进入冻结期，可在 " + graceDays + " 天内续期后恢复使用。短链：" + event.getFullShortUrl())
                .readFlag(0)
                .eventId(event.getEventId())
                .build();
        userNotificationService.save(notification);
    }

    /**
     * 冻结期结束后归档：仍为冻结状态才迁入历史库
     */
    private void handleArchive(ShortLinkExpireArchiveEvent event) {
        String fullShortUrl = event.getFullShortUrl();
        String gid = event.getGid();
        sendArchiveNotify(event);

        ShortLinkDO hotLink = shortLinkMapper.selectOne(Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, gid)
                .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.FROZEN.getEnableStatus()));
        if (hotLink != null) {
            archiveFromHot(hotLink);
            clearCache(fullShortUrl);
            return;
        }

        ShortLinkColdDO coldLink = shortLinkColdMapper.selectOne(Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, gid)
                .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl)
                .eq(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.FROZEN.getEnableStatus()));
        if (coldLink == null) {
            return;
        }
        archiveFromCold(coldLink);
        clearCache(fullShortUrl);
    }

    private void sendArchiveNotify(ShortLinkExpireArchiveEvent event) {
        if (event.getUserId() == null) {
            return;
        }
        UserNotificationDO notification = UserNotificationDO.builder()
                .userId(event.getUserId())
                .type(0)
                .title("短链已回收")
                .content("短链已进入历史归档，如需继续使用请重新创建。短链：" + event.getFullShortUrl())
                .readFlag(0)
                .eventId(event.getEventId())
                .build();
        userNotificationService.save(notification);
    }

    private boolean isExpired(ShortLinkDO link, Date now) {
        if (link.getValidDateType() == null || link.getValidDateType() == ValidDateTypeEnum.PERMANENT.getType()) {
            return false;
        }
        return link.getValidDate() != null && link.getValidDate().before(now);
    }

    private boolean isExpired(ShortLinkColdDO link, Date now) {
        if (link.getValidDateType() == null || link.getValidDateType() == ValidDateTypeEnum.PERMANENT.getType()) {
            return false;
        }
        return link.getValidDate() != null && link.getValidDate().before(now);
    }

    private void archiveFromHot(ShortLinkDO hotLink) {
        ShortLinkGoToDO hotGoto = shortLinkGoToMapper.selectOne(Wrappers.<ShortLinkGoToDO>lambdaQuery()
                .eq(ShortLinkGoToDO::getFullShortUrl, hotLink.getFullShortUrl()));

        shortLinkHistoryMapper.insert(BeanUtil.toBean(hotLink, ShortLinkHistoryDO.class));
        if (hotGoto != null) {
            shortLinkGoToHistoryMapper.insert(BeanUtil.toBean(hotGoto, ShortLinkGoToHistoryDO.class));
        }

        shortLinkMapper.deletePhysical(hotLink.getGid(), hotLink.getFullShortUrl());
        shortLinkGoToMapper.delete(Wrappers.<ShortLinkGoToDO>lambdaQuery()
                .eq(ShortLinkGoToDO::getFullShortUrl, hotLink.getFullShortUrl()));
    }

    private void archiveFromCold(ShortLinkColdDO coldLink) {
        ShortLinkGoToColdDO coldGoto = shortLinkGoToColdMapper.selectOne(Wrappers.<ShortLinkGoToColdDO>lambdaQuery()
                .eq(ShortLinkGoToColdDO::getFullShortUrl, coldLink.getFullShortUrl()));

        shortLinkHistoryMapper.insert(BeanUtil.toBean(coldLink, ShortLinkHistoryDO.class));
        if (coldGoto != null) {
            shortLinkGoToHistoryMapper.insert(BeanUtil.toBean(coldGoto, ShortLinkGoToHistoryDO.class));
        }

        shortLinkColdMapper.delete(Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, coldLink.getGid())
                .eq(ShortLinkColdDO::getFullShortUrl, coldLink.getFullShortUrl()));
        shortLinkGoToColdMapper.delete(Wrappers.<ShortLinkGoToColdDO>lambdaQuery()
                .eq(ShortLinkGoToColdDO::getFullShortUrl, coldLink.getFullShortUrl()));
    }

    private void clearCache(String fullShortUrl) {
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        cacheProducer.send(fullShortUrl);
    }
}
