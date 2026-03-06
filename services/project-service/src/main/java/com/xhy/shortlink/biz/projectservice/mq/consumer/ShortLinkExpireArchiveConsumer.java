/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xhy.shortlink.biz.projectservice.mq.consumer;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.biz.projectservice.config.ColdDataProperties;
import com.xhy.shortlink.biz.projectservice.common.constant.RocketMQConstant;
import com.xhy.shortlink.biz.projectservice.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToHistoryDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkHistoryDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToHistoryMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkHistoryMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.mq.event.ShortLinkExpireArchiveEvent;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkCacheProducer;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkExpireArchiveProducer;
import com.xhy.shortlink.framework.starter.common.enums.DelEnum;
import com.xhy.shortlink.framework.starter.idempotent.annotation.Idempotent;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentSceneEnum;
import com.xhy.shortlink.framework.starter.idempotent.enums.IdempotentTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.date.DateUtil;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

/**
 * 过期短链接归档消费者
 * <p>
 * FREEZE 阶段：将过期链接标记为冻结状态，投递 ARCHIVE 延迟消息。
 * ARCHIVE 阶段：宽限期结束后，将仍为冻结状态的链接迁入历史库并删除源表记录。
 *
 * @author XiaoYu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ColdDataProperties.class)
@RocketMQMessageListener(
        topic = RocketMQConstant.EXPIRE_ARCHIVE_TOPIC,
        consumerGroup = RocketMQConstant.EXPIRE_ARCHIVE_GROUP
)
public class ShortLinkExpireArchiveConsumer implements RocketMQListener<ShortLinkExpireArchiveEvent> {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final ShortLinkHistoryMapper shortLinkHistoryMapper;
    private final ShortLinkGoToHistoryMapper shortLinkGoToHistoryMapper;
    private final ShortLinkCacheProducer cacheProducer;
    private final ShortLinkExpireArchiveProducer expireArchiveProducer;
    private final ColdDataProperties coldDataProperties;

    @Override
    @Idempotent(
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            key = "#event.eventId",
            uniqueKeyPrefix = "expire-archive:",
            keyTimeout = 7200
    )
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ShortLinkExpireArchiveEvent event) {
        ShortLinkExpireArchiveEvent.Stage stage = event.getStage();
        if (stage == null) {
            stage = ShortLinkExpireArchiveEvent.Stage.FREEZE;
        }
        switch (stage) {
            case FREEZE -> handleFreeze(event);
            case ARCHIVE -> handleArchive(event);
            default -> log.warn("[过期归档] 未知阶段: {}", stage);
        }
    }

    /**
     * 冻结阶段：将过期链接标记为 FROZEN，投递 ARCHIVE 延迟消息
     */
    private void handleFreeze(ShortLinkExpireArchiveEvent event) {
        String fullShortUrl = event.getFullShortUrl();
        String gid = event.getGid();

        // 尝试在热库中冻结
        LambdaUpdateWrapper<ShortLinkDO> hotWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                .eq(ShortLinkDO::getGid, gid)
                .eq(ShortLinkDO::getDelFlag, DelEnum.NORMAL.getCode())
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getCode());
        ShortLinkDO freezeUpdate = ShortLinkDO.builder()
                .enableStatus(LinkEnableStatusEnum.FROZEN.getCode())
                .build();
        int hotUpdated = shortLinkMapper.update(freezeUpdate, hotWrapper);

        // 热库没找到，尝试冷库
        if (hotUpdated == 0) {
            LambdaUpdateWrapper<ShortLinkColdDO> coldWrapper = Wrappers.lambdaUpdate(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkColdDO::getGid, gid)
                    .eq(ShortLinkColdDO::getDelFlag, DelEnum.NORMAL.getCode())
                    .eq(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getCode());
            ShortLinkColdDO coldFreezeUpdate = ShortLinkColdDO.builder()
                    .enableStatus(LinkEnableStatusEnum.FROZEN.getCode())
                    .build();
            int coldUpdated = shortLinkColdMapper.update(coldFreezeUpdate, coldWrapper);
            if (coldUpdated == 0) {
                log.warn("[过期归档] 链接不存在或已处理，fullShortUrl={}", fullShortUrl);
                return;
            }
        }

        // 清除缓存
        clearCache(fullShortUrl);

        // 投递 ARCHIVE 延迟消息（宽限期后触发）
        int graceDays = coldDataProperties.getGraceDays();
        Date archiveAt = DateUtil.offsetDay(new Date(), graceDays);
        expireArchiveProducer.sendMessage(ShortLinkExpireArchiveEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .gid(gid)
                .fullShortUrl(fullShortUrl)
                .userId(event.getUserId())
                .expireAt(archiveAt)
                .stage(ShortLinkExpireArchiveEvent.Stage.ARCHIVE)
                .build());

        log.info("[过期归档] FREEZE 完成，fullShortUrl={}，ARCHIVE 将在 {} 天后触发", fullShortUrl, graceDays);
    }

    /**
     * 归档阶段：宽限期结束后，仍为 FROZEN 的链接迁入历史库并删除源记录
     */
    private void handleArchive(ShortLinkExpireArchiveEvent event) {
        String fullShortUrl = event.getFullShortUrl();
        String gid = event.getGid();

        // 优先从热表归档
        ShortLinkDO hotLink = shortLinkMapper.selectOne(Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, gid)
                .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.FROZEN.getCode()));
        if (hotLink != null) {
            archiveFromHot(hotLink);
            clearCache(fullShortUrl);
            log.info("[过期归档] ARCHIVE 完成（热表），fullShortUrl={}", fullShortUrl);
            return;
        }

        // 热表没有，从冷表归档
        ShortLinkColdDO coldLink = shortLinkColdMapper.selectOne(Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, gid)
                .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl)
                .eq(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.FROZEN.getCode()));
        if (coldLink != null) {
            archiveFromCold(coldLink);
            clearCache(fullShortUrl);
            log.info("[过期归档] ARCHIVE 完成（冷表），fullShortUrl={}", fullShortUrl);
        }
    }

    /** 从热表归档到历史库 */
    private void archiveFromHot(ShortLinkDO hotLink) {
        shortLinkHistoryMapper.insert(BeanUtil.toBean(hotLink, ShortLinkHistoryDO.class));
        ShortLinkGoToDO goTo = shortLinkGoToMapper.selectOne(Wrappers.<ShortLinkGoToDO>lambdaQuery()
                .eq(ShortLinkGoToDO::getFullShortUrl, hotLink.getFullShortUrl()));
        if (goTo != null) {
            shortLinkGoToHistoryMapper.insert(BeanUtil.toBean(goTo, ShortLinkGoToHistoryDO.class));
            shortLinkGoToMapper.delete(Wrappers.<ShortLinkGoToDO>lambdaQuery()
                    .eq(ShortLinkGoToDO::getFullShortUrl, hotLink.getFullShortUrl()));
        }
        shortLinkMapper.delete(Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, hotLink.getGid())
                .eq(ShortLinkDO::getFullShortUrl, hotLink.getFullShortUrl()));
    }

    /** 从冷表归档到历史库 */
    private void archiveFromCold(ShortLinkColdDO coldLink) {
        shortLinkHistoryMapper.insert(BeanUtil.toBean(coldLink, ShortLinkHistoryDO.class));
        ShortLinkGoToColdDO goTo = shortLinkGoToColdMapper.selectOne(Wrappers.<ShortLinkGoToColdDO>lambdaQuery()
                .eq(ShortLinkGoToColdDO::getFullShortUrl, coldLink.getFullShortUrl()));
        if (goTo != null) {
            shortLinkGoToHistoryMapper.insert(BeanUtil.toBean(goTo, ShortLinkGoToHistoryDO.class));
            shortLinkGoToColdMapper.delete(Wrappers.<ShortLinkGoToColdDO>lambdaQuery()
                    .eq(ShortLinkGoToColdDO::getFullShortUrl, coldLink.getFullShortUrl()));
        }
        shortLinkColdMapper.delete(Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, coldLink.getGid())
                .eq(ShortLinkColdDO::getFullShortUrl, coldLink.getFullShortUrl()));
    }

    /** 清除跳转缓存并广播本地缓存失效 */
    private void clearCache(String fullShortUrl) {
        try {
            cacheProducer.sendMessage(fullShortUrl);
        } catch (Exception e) {
            log.error("[过期归档] 清除缓存失败，fullShortUrl={}", fullShortUrl, e);
        }
    }
}
