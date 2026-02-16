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

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.biz.projectservice.common.constant.RocketMQConstant;
import com.xhy.shortlink.biz.projectservice.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

/**
 * 过期短链接归档消费者
 * <p>
 * Phase 1 仅实现 FREEZE 阶段：将过期链接标记为冻结状态，并投递 ARCHIVE 延迟消息。
 * ARCHIVE 阶段（冷数据迁移）留给 Phase 3 实现。
 *
 * @author XiaoYu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConstant.EXPIRE_ARCHIVE_TOPIC,
        consumerGroup = RocketMQConstant.EXPIRE_ARCHIVE_GROUP
)
public class ShortLinkExpireArchiveConsumer implements RocketMQListener<ShortLinkExpireArchiveEvent> {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkCacheProducer cacheProducer;
    private final ShortLinkExpireArchiveProducer expireArchiveProducer;

    @Value("${short-link.expire.grace-days:30}")
    private int graceDays;

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
            case ARCHIVE -> log.info("[过期归档] ARCHIVE 阶段暂未实现（Phase 3），fullShortUrl={}", event.getFullShortUrl());
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
        try {
            cacheProducer.sendMessage(fullShortUrl);
        } catch (Exception e) {
            log.error("[过期归档] 清除缓存失败，fullShortUrl={}", fullShortUrl, e);
        }

        // 投递 ARCHIVE 延迟消息（graceDays 后触发）
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
}
