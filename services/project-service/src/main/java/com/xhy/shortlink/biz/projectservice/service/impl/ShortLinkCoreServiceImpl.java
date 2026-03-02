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

package com.xhy.shortlink.biz.projectservice.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.*;
import com.xhy.shortlink.biz.projectservice.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.biz.projectservice.common.enums.OrderTagEnum;
import com.xhy.shortlink.biz.projectservice.common.enums.ValidDateTypeEnum;
import com.xhy.shortlink.biz.projectservice.config.GotoDomainWhiteListConfiguration;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.helper.ShortLinkCacheHelper;
import com.xhy.shortlink.biz.projectservice.metrics.ShortLinkMetrics;
import com.xhy.shortlink.biz.projectservice.mq.event.ShortLinkExpireArchiveEvent;
import com.xhy.shortlink.biz.projectservice.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.biz.projectservice.mq.event.UpdateFaviconEvent;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkCacheProducer;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkExpireArchiveProducer;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkRiskProducer;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkCoreService;
import com.xhy.shortlink.biz.projectservice.toolkit.LinkUtil;
import com.xhy.shortlink.framework.starter.common.enums.DelEnum;
import com.xhy.shortlink.framework.starter.common.toolkit.BeanUtil;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import com.xhy.shortlink.framework.starter.convention.exception.ServiceException;
import com.xhy.shortlink.framework.starter.designpattern.strategy.AbstractStrategyChoose;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.*;
import static com.xhy.shortlink.biz.projectservice.common.constant.ShortLinkConstant.HTTP_PROTOCOL;
import static com.xhy.shortlink.biz.projectservice.common.constant.ShortLinkConstant.TODAY_EXPIRETIME;

/**
 * 短链接核心 CRUD 服务实现
 *
 * @author XiaoYu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkCoreServiceImpl implements ShortLinkCoreService {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ShortLinkRiskProducer riskProducer;
    private final ShortLinkCacheProducer cacheProducer;
    private final ShortLinkExpireArchiveProducer expireArchiveProducer;
    private final ShortLinkCacheHelper cacheHelper;
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final PlatformTransactionManager transactionManager;
    private final DefaultRedisScript<Long> statsRankMigrateScript;
    private final ShortLinkMetrics shortLinkMetrics;

    @Value("${short-link.domain.default}")
    private String defaultDomain;

    @Value("${short-link.create.strategy}")
    private String createStrategy;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        try {
            verificationWhitelist(requestParam.getOriginUrl());
            // 策略模式选择方式生成短链接
            String suffix = abstractStrategyChoose.chooseAndExecuteResp(
                    createStrategy, requestParam.getOriginUrl() + "|" + defaultDomain);
            String fullShortUrl = defaultDomain + "/" + suffix;
            ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                    .gid(requestParam.getGid())
                    .createdType(requestParam.getCreatedType())
                    .domain(defaultDomain)
                    .description(requestParam.getDescription())
                    .validDateType(requestParam.getValidDateType())
                    .validDate(requestParam.getValidDate())
                    .fullShortUrl(fullShortUrl)
                    .originUrl(requestParam.getOriginUrl())
                    .shortUri(suffix)
                    .build();
            ShortLinkGoToDO shortLinkGoToDO = ShortLinkGoToDO.builder()
                    .gid(shortLinkDO.getGid())
                    .fullShortUrl(shortLinkDO.getFullShortUrl())
                    .build();
            try {
                shortLinkMapper.insert(shortLinkDO);
                shortLinkGoToMapper.insert(shortLinkGoToDO);
            } catch (DuplicateKeyException e) {
                if (!cacheHelper.bloomFilterContains(fullShortUrl)) {
                    cacheHelper.addToBloomFilter(fullShortUrl);
                }
                throw new ServiceException("短链接：" + fullShortUrl + " 已存在");
            }
            // 缓存预热
            cacheHelper.warmUp(shortLinkDO.getFullShortUrl(), shortLinkDO.getOriginUrl(),
                    shortLinkDO.getGid(), shortLinkDO.getValidDate());
            // 异步抓取图标
            eventPublisher.publishEvent(new UpdateFaviconEvent(
                    shortLinkDO.getFullShortUrl(), shortLinkDO.getGid(), requestParam.getOriginUrl()));
            // 发送 AI 风控审核消息
            riskProducer.sendMessage(ShortLinkRiskEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .fullShortUrl(shortLinkDO.getFullShortUrl())
                    .originUrl(shortLinkDO.getOriginUrl())
                    .gid(shortLinkDO.getGid())
                    .userId(Long.parseLong(UserContext.getUserId()))
                    .build());
            // 自定义有效期：发送过期归档延迟消息
            if (Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.CUSTOM.getType())
                    && requestParam.getValidDate() != null) {
                expireArchiveProducer.sendMessage(ShortLinkExpireArchiveEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .gid(shortLinkDO.getGid())
                        .fullShortUrl(shortLinkDO.getFullShortUrl())
                        .expireAt(requestParam.getValidDate())
                        .userId(Long.parseLong(UserContext.getUserId()))
                        .stage(ShortLinkExpireArchiveEvent.Stage.FREEZE)
                        .build());
            }
            shortLinkMetrics.recordCreateSuccess();
            return ShortLinkCreateRespDTO.builder()
                    .fullShortUrl(HTTP_PROTOCOL + shortLinkDO.getFullShortUrl())
                    .gid(shortLinkDO.getGid())
                    .originUrl(requestParam.getOriginUrl())
                    .build();
        } catch (RuntimeException ex) {
            shortLinkMetrics.recordCreateFailure();
            throw ex;
        }
    }

    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        List<String> originUrlList = requestParam.getOriginUrls();
        List<String> describeList = requestParam.getDescription();
        List<ShortLinkBaseInfoRespDTO> resultList = new ArrayList<>();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (int i = 0; i < originUrlList.size(); i++) {
            ShortLinkCreateReqDTO shortLinkCreateReqDTO = BeanUtil.convert(requestParam, ShortLinkCreateReqDTO.class);
            shortLinkCreateReqDTO.setOriginUrl(originUrlList.get(i));
            shortLinkCreateReqDTO.setDescription(describeList.get(i));
            try {
                ShortLinkCreateRespDTO shortLink = transactionTemplate.execute(status -> createShortLink(shortLinkCreateReqDTO));
                if (shortLink != null) {
                    resultList.add(ShortLinkBaseInfoRespDTO.builder()
                            .fullShortUrl(shortLink.getFullShortUrl())
                            .originUrl(shortLink.getOriginUrl())
                            .description(describeList.get(i))
                            .build());
                }
            } catch (Exception e) {
                log.error("批量创建短链接失败，原始参数：{}", originUrlList.get(i), e);
            }
        }
        return ShortLinkBatchCreateRespDTO.builder()
                .total(resultList.size())
                .baseLinkInfos(resultList)
                .build();
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        // 场景 A：如果用户点击了"今日访问量/人数/IP数"排序
        if (CharSequenceUtil.equalsAny(requestParam.getOrderTag(),
                OrderTagEnum.TODAY_PV.getValue(),
                OrderTagEnum.TODAY_UV.getValue(),
                OrderTagEnum.TODAY_UIP.getValue())) {
            return pageByRedisRank(requestParam);
        }
        // 场景 B：默认排序（按创建时间/总量），热库+冷库合并展示
        return pageHotColdByOrder(requestParam);
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        // 热库统计
        LambdaQueryWrapper<ShortLinkDO> hotWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .in(ShortLinkDO::getGid, requestParam)
                .eq(ShortLinkDO::getDelFlag, 0)
                .in(ShortLinkDO::getEnableStatus,
                        LinkEnableStatusEnum.ENABLE.getCode(),
                        LinkEnableStatusEnum.FROZEN.getCode())
                .groupBy(ShortLinkDO::getGid);
        List<ShortLinkGroupCountRespDTO> hotCounts = shortLinkMapper.selectGroupCount(hotWrapper);
        // 冷库统计
        LambdaQueryWrapper<ShortLinkColdDO> coldWrapper = Wrappers.lambdaQuery(ShortLinkColdDO.class)
                .in(ShortLinkColdDO::getGid, requestParam)
                .eq(ShortLinkColdDO::getDelFlag, 0)
                .in(ShortLinkColdDO::getEnableStatus,
                        LinkEnableStatusEnum.ENABLE.getCode(),
                        LinkEnableStatusEnum.FROZEN.getCode())
                .groupBy(ShortLinkColdDO::getGid);
        List<ShortLinkGroupCountRespDTO> coldCounts = shortLinkColdMapper.selectGroupCount(coldWrapper);
        // 合并热库+冷库
        Map<String, Integer> countMap = new HashMap<>();
        for (ShortLinkGroupCountRespDTO dto : hotCounts) {
            countMap.put(dto.getGid(), dto.getShortLinkCount());
        }
        for (ShortLinkGroupCountRespDTO dto : coldCounts) {
            countMap.merge(dto.getGid(), dto.getShortLinkCount(), Integer::sum);
        }
        return requestParam.stream()
                .map(gid -> ShortLinkGroupCountRespDTO.builder()
                        .gid(gid)
                        .shortLinkCount(countMap.getOrDefault(gid, 0))
                        .build())
                .toList();
    }

    @Override
    public void fillTodayStats(ShortLinkPageRespDTO requestParam) {
        String today = DateUtil.format(new Date(), "yyyyMMdd");
        String rankKey = String.format(RANK_KEY, requestParam.getGid(), requestParam.getFullShortUrl(), today);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(rankKey);
        if (entries.isEmpty()) {
            requestParam.setTodayPv(0);
            requestParam.setTodayUv(0);
            requestParam.setTodayUip(0);
        } else {
            requestParam.setTodayPv(Integer.parseInt(entries.getOrDefault("pv", "0").toString()));
            requestParam.setTodayUv(Integer.parseInt(entries.getOrDefault("uv", "0").toString()));
            requestParam.setTodayUip(Integer.parseInt(entries.getOrDefault("uip", "0").toString()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        verificationWhitelist(requestParam.getOriginUrl());
        // 1. 查出旧数据：热库优先，冷库兜底
        ShortLinkDO shortLinkDO = shortLinkMapper.selectOne(Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .in(ShortLinkDO::getEnableStatus,
                        LinkEnableStatusEnum.ENABLE.getCode(),
                        LinkEnableStatusEnum.FROZEN.getCode()));
        boolean isCold = false;
        if (shortLinkDO == null) {
            ShortLinkColdDO coldDO = shortLinkColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getGid, requestParam.getOriginGid())
                    .eq(ShortLinkColdDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkColdDO::getDelFlag, 0));
            if (coldDO != null) {
                shortLinkDO = BeanUtil.convert(coldDO, ShortLinkDO.class);
                isCold = true;
            }
        }
        if (shortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }
        // 2. 状态安全流转
        Integer oldStatus = shortLinkDO.getEnableStatus();
        Integer newStatus = oldStatus;
        if (Objects.equals(oldStatus, LinkEnableStatusEnum.ENABLE.getCode())
                || Objects.equals(oldStatus, LinkEnableStatusEnum.FROZEN.getCode())) {
            if (Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType())) {
                newStatus = LinkEnableStatusEnum.ENABLE.getCode();
            } else if (requestParam.getValidDate() != null) {
                newStatus = requestParam.getValidDate().after(new Date())
                        ? LinkEnableStatusEnum.ENABLE.getCode()
                        : LinkEnableStatusEnum.FROZEN.getCode();
            }
        }
        boolean isOriginUrlChanged = !Objects.equals(shortLinkDO.getOriginUrl(), requestParam.getOriginUrl());
        boolean isGidChanged = !Objects.equals(shortLinkDO.getGid(), requestParam.getGid());
        // 3. 执行更新
        if (!isCold && !isGidChanged) {
            // === 情况 A：热库数据 + 分组没变 → 原地 Update ===
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .set(ShortLinkDO::getOriginUrl, requestParam.getOriginUrl())
                    .set(ShortLinkDO::getDescription, requestParam.getDescription())
                    .set(ShortLinkDO::getValidDateType, requestParam.getValidDateType())
                    .set(ShortLinkDO::getEnableStatus, newStatus)
                    .set(ShortLinkDO::getValidDate,
                            Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType())
                                    ? null : requestParam.getValidDate());
            shortLinkMapper.update(null, updateWrapper);
        } else {
            // === 情况 B：分组改变 OR 数据在冷库 → 删旧插新（冷库回热） ===
            RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(
                    String.format(LOCK_GID_UPDATE_KEY, requestParam.getFullShortUrl()));
            RLock rLock = readWriteLock.writeLock();
            rLock.lock();
            try {
                // 3.1 删除旧路由（区分冷库/热库）
                if (isCold) {
                    shortLinkGoToColdMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                            .eq(ShortLinkGoToColdDO::getFullShortUrl, requestParam.getFullShortUrl())
                            .eq(ShortLinkGoToColdDO::getGid, requestParam.getOriginGid()));
                    shortLinkColdMapper.delete(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                            .eq(ShortLinkColdDO::getGid, requestParam.getOriginGid())
                            .eq(ShortLinkColdDO::getFullShortUrl, requestParam.getFullShortUrl()));
                } else {
                    shortLinkGoToMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                            .eq(ShortLinkGoToDO::getFullShortUrl, requestParam.getFullShortUrl())
                            .eq(ShortLinkGoToDO::getGid, requestParam.getOriginGid()));
                    shortLinkMapper.delete(Wrappers.lambdaQuery(ShortLinkDO.class)
                            .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                            .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl()));
                }
                // 3.2 插入新路由（始终插热库）
                shortLinkGoToMapper.insert(ShortLinkGoToDO.builder()
                        .gid(requestParam.getGid())
                        .fullShortUrl(requestParam.getFullShortUrl())
                        .build());
                // 3.3 插入新详情（始终插热库，实现冷库回热）
                shortLinkDO.setGid(requestParam.getGid());
                shortLinkDO.setOriginUrl(requestParam.getOriginUrl());
                shortLinkDO.setDescription(requestParam.getDescription());
                shortLinkDO.setValidDateType(requestParam.getValidDateType());
                shortLinkDO.setValidDate(Objects.equals(requestParam.getValidDateType(),
                        ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
                shortLinkDO.setEnableStatus(newStatus);
                shortLinkDO.setId(null);
                shortLinkMapper.insert(shortLinkDO);
                // 3.4 跨分组时迁移 Redis 今日排行数据
                if (isGidChanged) {
                    migrateRedisRankData(requestParam.getOriginGid(), requestParam.getGid(),
                            requestParam.getFullShortUrl());
                }
            } finally {
                rLock.unlock();
            }
        }
        // 4. originUrl 变更时：异步更新图标 + 风控审核
        if (isOriginUrlChanged) {
            eventPublisher.publishEvent(new UpdateFaviconEvent(
                    requestParam.getFullShortUrl(), requestParam.getGid(), requestParam.getOriginUrl()));
            riskProducer.sendMessage(ShortLinkRiskEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .fullShortUrl(requestParam.getFullShortUrl())
                    .originUrl(requestParam.getOriginUrl())
                    .gid(requestParam.getGid())
                    .userId(Long.parseLong(UserContext.getUserId()))
                    .build());
        }
        // 5. 清除 Redis 缓存 + 广播清除本地 Caffeine
        stringRedisTemplate.delete(Arrays.asList(
                String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()),
                String.format(GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl())));
        try {
            cacheProducer.sendMessage(requestParam.getFullShortUrl());
        } catch (Exception e) {
            log.error("修改短链接后清除缓存失败", e);
        }
    }

    /**
     * 跨分组时迁移 Redis ZSet 中的今日 PV/UV/UIP 排行数据
     */
    private void migrateRedisRankData(String oldGid, String newGid, String fullShortUrl) {
        String todayStr = DateUtil.today();
        List<String> statsTypes = Arrays.asList(
                OrderTagEnum.TODAY_PV.getValue(),
                OrderTagEnum.TODAY_UV.getValue(),
                OrderTagEnum.TODAY_UIP.getValue());
        for (String statsType : statsTypes) {
            String oldKey = String.format(RANK_KEY, statsType, oldGid, todayStr);
            String newKey = String.format(RANK_KEY, statsType, newGid, todayStr);
            stringRedisTemplate.execute(
                    statsRankMigrateScript,
                    Arrays.asList(oldKey, newKey),
                    fullShortUrl,
                    String.valueOf(TimeUnit.HOURS.toSeconds(TODAY_EXPIRETIME)));
        }
    }

    /**
     * 核心：基于 Redis ZSet 的分页查询：Redis (今日有数据) -> MySQL (今日无数据)
     */
    private IPage<ShortLinkPageRespDTO> pageByRedisRank(ShortLinkPageReqDTO req) {
        String todayStr = DateUtil.today();
        Date todayStart = DateUtil.beginOfDay(new Date());

        String rankKey = String.format(RANK_KEY, req.getOrderTag(), req.getGid(), todayStr);

        long current = req.getCurrent();
        long size = req.getSize();
        long start = (current - 1) * size;
        long end = start + size - 1;

        try {
            Long redisTotal = stringRedisTemplate.opsForZSet().zCard(rankKey);
            redisTotal = redisTotal == null ? 0 : redisTotal;

            long hotFallbackTotal = shortLinkMapper.countLinkFallback(req.getGid(), todayStart);
            long coldFallbackTotal = shortLinkColdMapper.countLinkFallback(req.getGid(), todayStart);

            long total = redisTotal + hotFallbackTotal + coldFallbackTotal;

            List<ShortLinkPageRespDTO> resultList = new ArrayList<>();

            if (start < redisTotal && end < redisTotal) {
                Set<String> urls = stringRedisTemplate.opsForZSet().reverseRange(rankKey, start, end);
                resultList.addAll(buildResultByUrls(urls, req.getGid()));
            } else if (start < redisTotal) {
                Set<String> urls = stringRedisTemplate.opsForZSet().reverseRange(rankKey, start, redisTotal - 1);
                resultList.addAll(buildResultByUrls(urls, req.getGid()));
                long needMore = size - resultList.size();
                resultList.addAll(pageHotColdFallback(req.getGid(), todayStart, 0, needMore));
            } else {
                long dbOffset = start - redisTotal;
                resultList.addAll(pageHotColdFallback(req.getGid(), todayStart, dbOffset, size));
            }

            IPage<ShortLinkPageRespDTO> page = new Page<>();
            page.setRecords(resultList);
            page.setTotal(total);
            page.setCurrent(current);
            page.setSize(size);
            return page;
        } catch (Exception e) {
            log.error("Redis排行榜查询失败，触发降级策略，转为纯数据库查询。Gid: {}", req.getGid(), e);
            return fallbackToBaseQuery(req);
        }
    }

    private IPage<ShortLinkPageRespDTO> fallbackToBaseQuery(ShortLinkPageReqDTO req) {
        return pageHotColdByOrder(req, false);
    }

    private IPage<ShortLinkPageRespDTO> pageHotColdByOrder(ShortLinkPageReqDTO req) {
        return pageHotColdByOrder(req, true);
    }

    private IPage<ShortLinkPageRespDTO> pageHotColdByOrder(ShortLinkPageReqDTO req, boolean fillToday) {
        long current = req.getCurrent();
        long size = req.getSize();
        long need = current * size;

        LambdaQueryWrapper<ShortLinkDO> hotWrapper = Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, req.getGid())
                .in(ShortLinkDO::getEnableStatus,
                    LinkEnableStatusEnum.ENABLE.getCode(),
                    LinkEnableStatusEnum.FROZEN.getCode())
                .eq(ShortLinkDO::getDelFlag, DelEnum.NORMAL.getCode());
        applyOrder(hotWrapper, req.getOrderTag());

        LambdaQueryWrapper<ShortLinkColdDO> coldWrapper =
                Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, req.getGid())
                .in(ShortLinkColdDO::getEnableStatus,
                    LinkEnableStatusEnum.ENABLE.getCode(),
                    LinkEnableStatusEnum.FROZEN.getCode())
                .eq(ShortLinkColdDO::getDelFlag, DelEnum.NORMAL.getCode());
        applyColdOrder(coldWrapper, req.getOrderTag());

        long hotTotal = shortLinkMapper.selectCount(hotWrapper);
        long coldTotal = shortLinkColdMapper.selectCount(coldWrapper);
        long total = hotTotal + coldTotal;

        Page<ShortLinkDO> hotPage = new Page<>(1, need);
        Page<ShortLinkColdDO> coldPage = new Page<>(1, need);
        List<ShortLinkDO> hotList = shortLinkMapper.selectPage(hotPage, hotWrapper).getRecords();
        List<ShortLinkColdDO> coldList =
                shortLinkColdMapper.selectPage(coldPage, coldWrapper).getRecords();

        List<ShortLinkPageRespDTO> merged = mergeHotColdList(hotList, coldList, req.getOrderTag(), fillToday);

        int fromIndex = (int) ((current - 1) * size);
        int toIndex = (int) Math.min(fromIndex + size, merged.size());
        List<ShortLinkPageRespDTO> pageRecords = fromIndex >= merged.size()
                ? new ArrayList<>()
                : merged.subList(fromIndex, toIndex);

        IPage<ShortLinkPageRespDTO> page = new Page<>();
        page.setRecords(pageRecords);
        page.setTotal(total);
        page.setCurrent(current);
        page.setSize(size);
        return page;
    }

    private void applyOrder(LambdaQueryWrapper<ShortLinkDO> wrapper, String orderTag) {
        if (CharSequenceUtil.equals(orderTag, "totalPv")) {
            wrapper.orderByDesc(ShortLinkDO::getTotalPv);
        } else if (CharSequenceUtil.equals(orderTag, "totalUv")) {
            wrapper.orderByDesc(ShortLinkDO::getTotalUv);
        } else if (CharSequenceUtil.equals(orderTag, "totalUip")) {
            wrapper.orderByDesc(ShortLinkDO::getTotalUip);
        } else {
            wrapper.orderByDesc(ShortLinkDO::getCreateTime);
        }
    }

    private void applyColdOrder(LambdaQueryWrapper<ShortLinkColdDO> wrapper, String orderTag) {
        if (CharSequenceUtil.equals(orderTag, "totalPv")) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalPv);
        } else if (CharSequenceUtil.equals(orderTag, "totalUv")) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUv);
        } else if (CharSequenceUtil.equals(orderTag, "totalUip")) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUip);
        } else {
            wrapper.orderByDesc(ShortLinkColdDO::getCreateTime);
        }
    }

    private List<ShortLinkPageRespDTO> mergeHotColdList(
            List<ShortLinkDO> hotList,
            List<ShortLinkColdDO> coldList,
            String orderTag,
            boolean fillToday) {
        List<ShortLinkPageRespDTO> merged = new ArrayList<>();

        for (ShortLinkDO hot : hotList) {
            ShortLinkPageRespDTO dto = BeanUtil.convert(hot, ShortLinkPageRespDTO.class);
            dto.setDomain(HTTP_PROTOCOL + dto.getDomain());
            if (fillToday) {
                fillTodayStats(dto);
            } else {
                dto.setTodayPv(0);
                dto.setTodayUv(0);
                dto.setTodayUip(0);
            }
            merged.add(dto);
        }

        for (ShortLinkColdDO cold : coldList) {
            ShortLinkPageRespDTO dto = BeanUtil.convert(cold, ShortLinkPageRespDTO.class);
            dto.setDomain(HTTP_PROTOCOL + dto.getDomain());
            if (fillToday) {
                fillTodayStats(dto);
            } else {
                dto.setTodayPv(0);
                dto.setTodayUv(0);
                dto.setTodayUip(0);
            }
            merged.add(dto);
        }

        merged.sort(buildOrderComparator(orderTag));
        return merged;
    }

    private Comparator<ShortLinkPageRespDTO> buildOrderComparator(String orderTag) {
        if (CharSequenceUtil.equals(orderTag, "totalPv")) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) ->
                    Optional.ofNullable(dto.getTotalPv()).orElse(0)).reversed()
                    .thenComparing(dto -> Optional.ofNullable(dto.getCreateTime()).orElse(new Date(0)),
                            Comparator.reverseOrder());
        }
        if (CharSequenceUtil.equals(orderTag, "totalUv")) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) ->
                    Optional.ofNullable(dto.getTotalUv()).orElse(0)).reversed()
                    .thenComparing(dto -> Optional.ofNullable(dto.getCreateTime()).orElse(new Date(0)),
                            Comparator.reverseOrder());
        }
        if (CharSequenceUtil.equals(orderTag, "totalUip")) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) ->
                    Optional.ofNullable(dto.getTotalUip()).orElse(0)).reversed()
                    .thenComparing(dto -> Optional.ofNullable(dto.getCreateTime()).orElse(new Date(0)),
                            Comparator.reverseOrder());
        }
        return Comparator.comparing((ShortLinkPageRespDTO dto) ->
                Optional.ofNullable(dto.getCreateTime()).orElse(new Date(0)), Comparator.reverseOrder());
    }

    private List<ShortLinkPageRespDTO> pageHotColdFallback(String gid, Date todayStart, long offset, long size) {
        long need = offset + size;

        LambdaQueryWrapper<ShortLinkDO> hotWrapper = Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, gid)
                .in(ShortLinkDO::getEnableStatus,
                    LinkEnableStatusEnum.ENABLE.getCode(),
                    LinkEnableStatusEnum.FROZEN.getCode())
                .eq(ShortLinkDO::getDelFlag, DelEnum.NORMAL.getCode())
                .and(q -> q.lt(ShortLinkDO::getLastAccessTime, todayStart)
                        .or()
                        .isNull(ShortLinkDO::getLastAccessTime))
                .orderByDesc(ShortLinkDO::getCreateTime);

        LambdaQueryWrapper<ShortLinkColdDO> coldWrapper =
                Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, gid)
                .in(ShortLinkColdDO::getEnableStatus,
                    LinkEnableStatusEnum.ENABLE.getCode(),
                    LinkEnableStatusEnum.FROZEN.getCode())
                .eq(ShortLinkColdDO::getDelFlag, DelEnum.NORMAL.getCode())
                .and(q -> q.lt(ShortLinkColdDO::getLastAccessTime, todayStart)
                        .or()
                        .isNull(ShortLinkColdDO::getLastAccessTime))
                .orderByDesc(ShortLinkColdDO::getCreateTime);

        Page<ShortLinkDO> hotPage = new Page<>(1, need);
        Page<ShortLinkColdDO> coldPage = new Page<>(1, need);
        List<ShortLinkDO> hotList = shortLinkMapper.selectPage(hotPage, hotWrapper).getRecords();
        List<ShortLinkColdDO> coldList =
                shortLinkColdMapper.selectPage(coldPage, coldWrapper).getRecords();

        List<ShortLinkPageRespDTO> merged = mergeHotColdList(hotList, coldList, null, false);

        int fromIndex = (int) offset;
        int toIndex = (int) Math.min(fromIndex + size, merged.size());
        return fromIndex >= merged.size() ? new ArrayList<>() : merged.subList(fromIndex, toIndex);
    }

    private List<ShortLinkPageRespDTO> buildResultByUrls(Set<String> urls, String gid) {
        if (urls == null || urls.isEmpty()) {
            return new ArrayList<>();
        }

        List<ShortLinkPageRespDTO> resultList = new ArrayList<>();

        for (String fullShortUrl : urls) {
            LambdaQueryWrapper<ShortLinkDO> hotWrapper = Wrappers.<ShortLinkDO>lambdaQuery()
                    .eq(ShortLinkDO::getGid, gid)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getDelFlag, DelEnum.NORMAL.getCode());
            ShortLinkDO hotLink = shortLinkMapper.selectOne(hotWrapper);

            if (hotLink != null) {
                ShortLinkPageRespDTO dto = BeanUtil.convert(hotLink, ShortLinkPageRespDTO.class);
                dto.setDomain(HTTP_PROTOCOL + dto.getDomain());
                fillTodayStats(dto);
                resultList.add(dto);
            } else {
                LambdaQueryWrapper<ShortLinkColdDO> coldWrapper =
                        Wrappers.<ShortLinkColdDO>lambdaQuery()
                        .eq(ShortLinkColdDO::getGid, gid)
                        .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl)
                        .eq(ShortLinkColdDO::getDelFlag, DelEnum.NORMAL.getCode());
                ShortLinkColdDO coldLink = shortLinkColdMapper.selectOne(coldWrapper);

                if (coldLink != null) {
                    ShortLinkPageRespDTO dto = BeanUtil.convert(coldLink, ShortLinkPageRespDTO.class);
                    dto.setDomain(HTTP_PROTOCOL + dto.getDomain());
                    fillTodayStats(dto);
                    resultList.add(dto);
                }
            }
        }

        return resultList;
    }

    private void verificationWhitelist(String originUrl) {
        Boolean enable = gotoDomainWhiteListConfiguration.getEnable();
        if (enable == null || !enable) {
            return;
        }
        String domain = LinkUtil.extractDomain(originUrl);
        if (CharSequenceUtil.isBlank(domain)) {
            throw new ClientException("跳转链接填写错误");
        }
        List<String> details = gotoDomainWhiteListConfiguration.getDetails();
        if (!details.contains(domain)) {
            throw new ClientException("请生成以下网站跳转链接：" + gotoDomainWhiteListConfiguration.getNames());
        }
    }
}
