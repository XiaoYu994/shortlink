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
import com.xhy.shortlink.biz.projectservice.service.RecycleBinService;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkCoreService;
import com.xhy.shortlink.framework.starter.common.toolkit.BeanUtil;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;
import static com.xhy.shortlink.biz.projectservice.common.constant.ShortLinkConstant.HTTP_PROTOCOL;

/**
 * 回收站服务实现
 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkCoreService shortLinkCoreService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void recycleBinSave(ShortLinkRecycleBinSaveReqDTO requestParam) {
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getCode());
        shortLinkMapper.update(ShortLinkDO.builder().enableStatus(LinkEnableStatusEnum.NOT_ENABLED.getCode()).build(), updateWrapper);
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
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

        List<ShortLinkPageRespDTO> merged = new ArrayList<>();
        hotPage.getRecords().forEach(each -> {
            ShortLinkPageRespDTO dto = BeanUtil.convert(each, ShortLinkPageRespDTO.class);
            dto.setDomain(HTTP_PROTOCOL + each.getDomain());
            shortLinkCoreService.fillTodayStats(dto);
            merged.add(dto);
        });
        coldPage.getRecords().forEach(each -> {
            ShortLinkPageRespDTO dto = BeanUtil.convert(each, ShortLinkPageRespDTO.class);
            dto.setDomain(HTTP_PROTOCOL + each.getDomain());
            shortLinkCoreService.fillTodayStats(dto);
            merged.add(dto);
        });

        merged.sort(buildComparator(requestParam.getOrderTag()));
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
    public void recoverShortlink(ShortLinkRecycleBinRecoverReqDTO requestParam) {
        if (requestParam.getEnableStatus() == LinkEnableStatusEnum.BANNED.getCode()) {
            throw new ClientException("短链接被封禁，无法恢复，请联系客服解封后重试");
        }
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.NOT_ENABLED.getCode());
        shortLinkMapper.update(ShortLinkDO.builder().enableStatus(LinkEnableStatusEnum.ENABLE.getCode()).build(), updateWrapper);
        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public void removeShortlink(ShortLinkRecycleBinRemoveReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .in(ShortLinkDO::getEnableStatus,
                        LinkEnableStatusEnum.NOT_ENABLED.getCode(),
                        LinkEnableStatusEnum.BANNED.getCode());
        shortLinkMapper.delete(queryWrapper);
    }

    private void applyColdOrder(LambdaQueryWrapper<ShortLinkColdDO> wrapper, String orderTag) {
        if ("totalPv".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalPv);
        } else if ("totalUv".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUv);
        } else if ("totalUip".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUip);
        } else {
            wrapper.orderByDesc(ShortLinkColdDO::getCreateTime);
        }
    }

    private Comparator<ShortLinkPageRespDTO> buildComparator(String orderTag) {
        if ("totalPv".equals(orderTag)) {
            return Comparator.comparing((ShortLinkPageRespDTO d) -> d.getTotalPv() == null ? 0 : d.getTotalPv()).reversed();
        }
        if ("totalUv".equals(orderTag)) {
            return Comparator.comparing((ShortLinkPageRespDTO d) -> d.getTotalUv() == null ? 0 : d.getTotalUv()).reversed();
        }
        if ("totalUip".equals(orderTag)) {
            return Comparator.comparing((ShortLinkPageRespDTO d) -> d.getTotalUip() == null ? 0 : d.getTotalUip()).reversed();
        }
        return Comparator.comparing(ShortLinkPageRespDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
