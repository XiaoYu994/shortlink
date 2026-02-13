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

import cn.hutool.core.text.CharSequenceUtil;
import com.xhy.shortlink.biz.projectservice.config.GotoDomainWhiteListConfiguration;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkBaseInfoRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.biz.projectservice.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.biz.projectservice.mq.event.UpdateFaviconEvent;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkRiskProducer;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkService;
import com.xhy.shortlink.biz.projectservice.toolkit.LinkUtil;
import com.xhy.shortlink.framework.starter.designpattern.strategy.AbstractStrategyChoose;
import com.xhy.shortlink.framework.starter.common.toolkit.BeanUtil;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import com.xhy.shortlink.framework.starter.convention.exception.ServiceException;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;
import static com.xhy.shortlink.biz.projectservice.common.constant.ShortLinkConstant.HTTP_PROTOCOL;

/**
 * 短链接创建服务实现
 *
 * @author XiaoYu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RBloomFilter<String> shortlinkUriCreateCachePenetrationBloomFilter;
    private final ApplicationEventPublisher eventPublisher;
    private final ShortLinkRiskProducer riskProducer;
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final PlatformTransactionManager transactionManager;

    @Value("${short-link.domain.default}")
    private String defaultDomain;

    @Value("${short-link.create.strategy}")
    private String createStrategy;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
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
            if (!shortlinkUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
                shortlinkUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
            }
            throw new ServiceException("短链接：" + fullShortUrl + " 已存在");
        }
        // 缓存预热
        long validTimeStamp = (shortLinkDO.getValidDate() != null) ? shortLinkDO.getValidDate().getTime() : -1;
        String cacheValue = String.format("%d|%s|%s", validTimeStamp, shortLinkDO.getOriginUrl(), shortLinkDO.getGid());
        long initialTTL = LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate());
        stringRedisTemplate.opsForValue().set(
                String.format(GOTO_SHORT_LINK_KEY, shortLinkDO.getFullShortUrl()),
                cacheValue, initialTTL, TimeUnit.MILLISECONDS);
        // 删除空值缓存
        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        // 加入布隆过滤器
        shortlinkUriCreateCachePenetrationBloomFilter.add(shortLinkDO.getFullShortUrl());
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
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl(HTTP_PROTOCOL + shortLinkDO.getFullShortUrl())
                .gid(shortLinkDO.getGid())
                .originUrl(requestParam.getOriginUrl())
                .build();
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
