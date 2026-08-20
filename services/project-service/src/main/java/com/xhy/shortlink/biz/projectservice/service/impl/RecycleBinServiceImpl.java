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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.projectservice.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.helper.ShortLinkCacheHelper;
import com.xhy.shortlink.biz.projectservice.service.RecycleBinService;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkColdDataService;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkCoreService;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 回收站服务实现
 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private static final long MAX_PAGE_SIZE = 100;
    private static final long MAX_PAGE_CURRENT = 10_000;
    private static final long MAX_PAGE_RECORDS = 10_000;

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkCoreService shortLinkCoreService;
    private final ShortLinkColdDataService shortLinkColdDataService;
    private final ShortLinkCacheHelper cacheHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recycleBinSave(ShortLinkRecycleBinSaveReqDTO requestParam) {
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getCode());
        int updated = shortLinkMapper.update(ShortLinkDO.builder().enableStatus(LinkEnableStatusEnum.NOT_ENABLED.getCode()).build(), updateWrapper);
        if (updated == 0) {
            shortLinkColdMapper.update(ShortLinkColdDO.builder()
                    .enableStatus(LinkEnableStatusEnum.NOT_ENABLED.getCode()).build(),
                    Wrappers.lambdaUpdate(ShortLinkColdDO.class)
                            .eq(ShortLinkColdDO::getGid, requestParam.getGid())
                            .eq(ShortLinkColdDO::getFullShortUrl, requestParam.getFullShortUrl())
                            .eq(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getCode()));
        }
        clearCache(requestParam.getFullShortUrl());
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        validatePage(requestParam);
        long current = requestParam.getCurrent();
        long size = requestParam.getSize();
        long need = current * size;

        ShortLinkRecycleBinPageReqDTO hotReq = new ShortLinkRecycleBinPageReqDTO();
        hotReq.setCurrent(1);
        hotReq.setSize(need);
        hotReq.setOrderTag(requestParam.getOrderTag());
        hotReq.setGidList(requestParam.getGidList());
        IPage<ShortLinkDO> hotPage = shortLinkMapper.pageRecycleBinLink(hotReq);

        LambdaQueryWrapper<ShortLinkColdDO> coldWrapper = Wrappers.lambdaQuery(ShortLinkColdDO.class)
                .in(ShortLinkColdDO::getEnableStatus,
                        LinkEnableStatusEnum.NOT_ENABLED.getCode(),
                        LinkEnableStatusEnum.BANNED.getCode())
                .eq(ShortLinkColdDO::getDelFlag, 0);
        if (requestParam.getGidList() != null && !requestParam.getGidList().isEmpty()) {
            coldWrapper.in(ShortLinkColdDO::getGid, requestParam.getGidList());
        }
        applyColdOrder(coldWrapper, requestParam.getOrderTag());
        Page<ShortLinkColdDO> coldPage = shortLinkColdMapper.selectPage(new Page<>(1, need), coldWrapper);

        List<ShortLinkPageRespDTO> merged = shortLinkColdDataService.mergeSorted(
                hotPage.getRecords(),
                coldPage.getRecords(),
                requestParam.getOrderTag(),
                shortLinkCoreService::fillTodayStats);
        int fromIndex = (int) ((current - 1) * size);
        int toIndex = (int) Math.min(fromIndex + size, merged.size());
        List<ShortLinkPageRespDTO> pageRecords = fromIndex >= merged.size()
                ? new ArrayList<>() : merged.subList(fromIndex, toIndex);

        Page<ShortLinkPageRespDTO> result = new Page<>(current, size);
        result.setRecords(pageRecords);
        result.setTotal(hotPage.getTotal() + coldPage.getTotal());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverShortlink(ShortLinkRecycleBinRecoverReqDTO requestParam) {
        if (requestParam.getEnableStatus() == LinkEnableStatusEnum.BANNED.getCode()) {
            throw new ClientException("短链接被封禁，无法恢复，请联系客服解封后重试");
        }
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.NOT_ENABLED.getCode());
        int updated = shortLinkMapper.update(ShortLinkDO.builder().enableStatus(LinkEnableStatusEnum.ENABLE.getCode()).build(), updateWrapper);
        if (updated == 0) {
            shortLinkColdMapper.update(ShortLinkColdDO.builder()
                    .enableStatus(LinkEnableStatusEnum.ENABLE.getCode()).build(),
                    Wrappers.lambdaUpdate(ShortLinkColdDO.class)
                            .eq(ShortLinkColdDO::getGid, requestParam.getGid())
                            .eq(ShortLinkColdDO::getFullShortUrl, requestParam.getFullShortUrl())
                            .eq(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.NOT_ENABLED.getCode()));
        }
        clearCache(requestParam.getFullShortUrl());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeShortlink(ShortLinkRecycleBinRemoveReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .in(ShortLinkDO::getEnableStatus,
                        LinkEnableStatusEnum.NOT_ENABLED.getCode(),
                        LinkEnableStatusEnum.BANNED.getCode());
        int deleted = shortLinkMapper.delete(queryWrapper);
        if (deleted == 0) {
            shortLinkColdMapper.delete(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getGid, requestParam.getGid())
                    .eq(ShortLinkColdDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .in(ShortLinkColdDO::getEnableStatus,
                            LinkEnableStatusEnum.NOT_ENABLED.getCode(),
                            LinkEnableStatusEnum.BANNED.getCode()));
        }
        clearCache(requestParam.getFullShortUrl());
    }

    private void applyColdOrder(LambdaQueryWrapper<ShortLinkColdDO> wrapper, String orderTag) {
        if ("totalPv".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalPv).orderByDesc(ShortLinkColdDO::getCreateTime);
        } else if ("totalUv".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUv).orderByDesc(ShortLinkColdDO::getCreateTime);
        } else if ("totalUip".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUip).orderByDesc(ShortLinkColdDO::getCreateTime);
        } else {
            wrapper.orderByDesc(ShortLinkColdDO::getCreateTime);
        }
    }

    private void clearCache(String fullShortUrl) {
        cacheHelper.invalidate(fullShortUrl);
    }

    private void validatePage(ShortLinkRecycleBinPageReqDTO requestParam) {
        if (requestParam == null || requestParam.getCurrent() < 1
                || requestParam.getCurrent() > MAX_PAGE_CURRENT
                || requestParam.getSize() < 1 || requestParam.getSize() > MAX_PAGE_SIZE
                || requestParam.getCurrent() * requestParam.getSize() > MAX_PAGE_RECORDS) {
            throw new ClientException("分页参数无效：页码范围为 1-" + MAX_PAGE_CURRENT
                    + "，每页数量范围为 1-" + MAX_PAGE_SIZE
                    + "，单次最多查询 " + MAX_PAGE_RECORDS + " 条");
        }
    }

}
