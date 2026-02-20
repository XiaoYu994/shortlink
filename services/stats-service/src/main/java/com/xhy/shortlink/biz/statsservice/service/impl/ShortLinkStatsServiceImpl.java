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

package com.xhy.shortlink.biz.statsservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.biz.statsservice.dao.entity.*;
import com.xhy.shortlink.biz.statsservice.dao.mapper.*;
import com.xhy.shortlink.biz.statsservice.dto.req.*;
import com.xhy.shortlink.biz.statsservice.dto.resp.*;
import com.xhy.shortlink.biz.statsservice.service.ShortLinkStatsService;

import static com.xhy.shortlink.biz.statsservice.common.constant.StatsColumnConstant.*;
import com.xhy.shortlink.framework.starter.convention.exception.ServiceException;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 短链接统计查询服务实现
 *
 * @author XiaoYu
 */
@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {

    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkGroupMapper groupMapper;

    @Override
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        checkGroupBelongToUser(requestParam.getGid());
        List<LinkAccessStatsDO> linkAccessStatsDOList = linkAccessStatsMapper.listStatsByShortLink(requestParam);
        if (CollUtil.isEmpty(linkAccessStatsDOList)) {
            return null;
        }
        LinkAccessStatsDO pvUvUidStats = linkAccessLogsMapper.findPvUvUidStatsByShortLink(requestParam);
        // 地区统计
        List<ShortLinkStatsLocaleCNRespDTO> localeCnStats = new ArrayList<>();
        List<LinkLocaleStatsDO> localeList = linkLocaleStatsMapper.listLocaleByShortLink(requestParam);
        int localCntSum = localeList.stream().mapToInt(LinkLocaleStatsDO::getCnt).sum();
        localeList.forEach(each -> localeCnStats.add(ShortLinkStatsLocaleCNRespDTO.builder()
                .cnt(each.getCnt()).locale(each.getProvince())
                .ratio(Math.round(each.getCnt() * 1.0 / localCntSum * 100) / 100.0).build()));
        // 小时统计
        List<Integer> hourStats = buildHourStats(linkAccessStatsMapper.listHourStatsByShortLink(requestParam));
        // 高频IP
        List<ShortLinkStatsTopIpRespDTO> topIpStats = buildTopIpStats(linkAccessLogsMapper.listTopIpByShortLink(requestParam));
        // 星期统计
        List<Integer> weekdayStats = buildWeekdayStats(linkAccessStatsMapper.listWeekdayStatsByShortLink(requestParam));
        // 浏览器统计
        List<ShortLinkStatsBrowserRespDTO> browserStats = buildBrowserStats(linkBrowserStatsMapper.listBrowserStatsByShortLink(requestParam));
        // OS统计
        List<ShortLinkStatsOsRespDTO> osStats = buildOsStats(linkOsStatsMapper.listOsStatsByShortLink(requestParam));
        // UV类型统计
        List<ShortLinkStatsUvRespDTO> uvTypeStats = buildUvTypeStats(linkAccessLogsMapper.findUvTypeCntByShortLink(requestParam));
        // 设备统计
        List<ShortLinkStatsDeviceRespDTO> deviceStats = buildDeviceStats(linkDeviceStatsMapper.listDeviceStatsByShortLink(requestParam));
        // 网络统计
        List<ShortLinkStatsNetworkRespDTO> networkStats = buildNetworkStats(linkNetworkStatsMapper.listNetworkStatsByShortLink(requestParam));

        return ShortLinkStatsRespDTO.builder()
                .pv(pvUvUidStats.getPv()).uv(pvUvUidStats.getUv()).uip(pvUvUidStats.getUip())
                .daily(BeanUtil.copyToList(linkAccessStatsDOList, ShortLinkStatsAccessDailyRespDTO.class))
                .localeCnStats(localeCnStats).hourStats(hourStats).topIpStats(topIpStats)
                .weekdayStats(weekdayStats).browserStats(browserStats).osStats(osStats)
                .uvTypeStats(uvTypeStats).deviceStats(deviceStats).networkStats(networkStats)
                .build();
    }

    @Override
    public ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkStatsGroupReqDTO requestParam) {
        checkGroupBelongToUser(requestParam.getGid());
        List<LinkAccessStatsDO> linkAccessStatsDOList = linkAccessStatsMapper.listStatsByShortLinkGroup(requestParam);
        if (CollUtil.isEmpty(linkAccessStatsDOList)) {
            return null;
        }
        LinkAccessStatsDO pvUvUidStats = linkAccessLogsMapper.findPvUvUidStatsByShortLinkGroup(requestParam);
        List<ShortLinkStatsLocaleCNRespDTO> localeCnStats = new ArrayList<>();
        List<LinkLocaleStatsDO> localeList = linkLocaleStatsMapper.listLocaleByShortLinkGroup(requestParam);
        int localCntSum = localeList.stream().mapToInt(LinkLocaleStatsDO::getCnt).sum();
        localeList.forEach(each -> localeCnStats.add(ShortLinkStatsLocaleCNRespDTO.builder()
                .cnt(each.getCnt()).locale(each.getProvince())
                .ratio(Math.round(each.getCnt() * 1.0 / localCntSum * 100) / 100.0).build()));
        List<Integer> hourStats = buildHourStats(linkAccessStatsMapper.listHourStatsByShortLinkGroup(requestParam));
        List<ShortLinkStatsTopIpRespDTO> topIpStats = buildTopIpStats(linkAccessLogsMapper.listTopIpByShortLinkGroup(requestParam));
        List<Integer> weekdayStats = buildWeekdayStats(linkAccessStatsMapper.listWeekdayStatsByShortLinkGroup(requestParam));
        List<ShortLinkStatsBrowserRespDTO> browserStats = buildBrowserStats(linkBrowserStatsMapper.listBrowserStatsByShortLinkGroup(requestParam));
        List<ShortLinkStatsOsRespDTO> osStats = buildOsStats(linkOsStatsMapper.listOsStatsByShortLinkGroup(requestParam));
        List<ShortLinkStatsUvRespDTO> uvTypeStats = buildUvTypeStats(linkAccessLogsMapper.findUvTypeCntByShortLinkGroup(requestParam));
        List<ShortLinkStatsDeviceRespDTO> deviceStats = buildDeviceStats(linkDeviceStatsMapper.listDeviceStatsByShortLinkGroup(requestParam));
        List<ShortLinkStatsNetworkRespDTO> networkStats = buildNetworkStats(linkNetworkStatsMapper.listNetworkStatsByShortLinkGroup(requestParam));

        return ShortLinkStatsRespDTO.builder()
                .pv(pvUvUidStats.getPv()).uv(pvUvUidStats.getUv()).uip(pvUvUidStats.getUip())
                .daily(BeanUtil.copyToList(linkAccessStatsDOList, ShortLinkStatsAccessDailyRespDTO.class))
                .localeCnStats(localeCnStats).hourStats(hourStats).topIpStats(topIpStats)
                .weekdayStats(weekdayStats).browserStats(browserStats).osStats(osStats)
                .uvTypeStats(uvTypeStats).deviceStats(deviceStats).networkStats(networkStats)
                .build();
    }

    @Override
    public IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam) {
        checkGroupBelongToUser(requestParam.getGid());
        LambdaQueryWrapper<LinkAccessLogsDO> queryWrapper = Wrappers.lambdaQuery(LinkAccessLogsDO.class)
                .eq(LinkAccessLogsDO::getFullShortUrl, requestParam.getFullShortUrl())
                .between(LinkAccessLogsDO::getCreateTime, requestParam.getStartDate(), requestParam.getEndDate())
                .orderByDesc(LinkAccessLogsDO::getCreateTime);
        IPage<LinkAccessLogsDO> logPage = linkAccessLogsMapper.selectPage(requestParam, queryWrapper);
        if (CollUtil.isEmpty(logPage.getRecords())) {
            return new Page<>();
        }
        IPage<ShortLinkStatsAccessRecordRespDTO> result = logPage.convert(
                each -> BeanUtil.toBean(each, ShortLinkStatsAccessRecordRespDTO.class));
        List<String> userList = result.getRecords().stream()
                .map(ShortLinkStatsAccessRecordRespDTO::getUser).toList();
        List<Map<String, Object>> uvTypeList = linkAccessLogsMapper.selectUvTypeByUser(
                ShortLinkUvTypeReqDTO.builder()
                        .fullShortUrl(requestParam.getFullShortUrl())
                        .gid(requestParam.getGid())
                        .enableStatus(requestParam.getEnableStatus())
                        .startDate(requestParam.getStartDate())
                        .endDate(requestParam.getEndDate())
                        .userAccessLogsList(userList).build());
        fillUvType(result, uvTypeList);
        return result;
    }

    @Override
    public IPage<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(ShortLinkStatsAccessRecordGroupReqDTO requestParam) {
        checkGroupBelongToUser(requestParam.getGid());
        IPage<LinkAccessLogsDO> logPage = linkAccessLogsMapper.selectGroupPage(requestParam);
        if (CollUtil.isEmpty(logPage.getRecords())) {
            return new Page<>();
        }
        IPage<ShortLinkStatsAccessRecordRespDTO> result = logPage.convert(
                each -> BeanUtil.toBean(each, ShortLinkStatsAccessRecordRespDTO.class));
        List<String> userList = result.getRecords().stream()
                .map(ShortLinkStatsAccessRecordRespDTO::getUser).toList();
        List<Map<String, Object>> uvTypeList = linkAccessLogsMapper.selectUvTypeByUserGruop(
                ShortLinkUvTypeReqDTO.builder()
                        .gid(requestParam.getGid())
                        .startDate(requestParam.getStartDate())
                        .endDate(requestParam.getEndDate())
                        .userAccessLogsList(userList).build());
        fillUvType(result, uvTypeList);
        return result;
    }

    // ==================== private helpers ====================

    /**
     * 校验分组是否属于当前登录用户
     */
    private void checkGroupBelongToUser(String gid) {
        String username = Optional.ofNullable(UserContext.getUsername())
                .orElseThrow(() -> new ServiceException("用户未登录"));
        LambdaQueryWrapper<GroupDO> qw = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid).eq(GroupDO::getUsername, username);
        if (CollUtil.isEmpty(groupMapper.selectList(qw))) {
            throw new ServiceException("用户信息与分组标识不匹配");
        }
    }

    /**
     * 填充访客类型（新/旧访客）到访问记录
     */
    private void fillUvType(IPage<ShortLinkStatsAccessRecordRespDTO> result,
                            List<Map<String, Object>> uvTypeList) {
        result.getRecords().forEach(each -> each.setUvType(
                uvTypeList.stream()
                        .filter(uv -> Objects.equals(each.getUser(), uv.get(COL_USER)))
                        .findFirst()
                        .map(uv -> uv.get(COL_UV_TYPE).toString())
                        .orElse(UV_TYPE_OLD_LABEL)));
    }

    /**
     * 构建 0~23 小时 PV 统计列表
     */
    private List<Integer> buildHourStats(List<LinkAccessStatsDO> hourData) {
        Map<Integer, Integer> hourMap = hourData.stream()
                .collect(Collectors.groupingBy(LinkAccessStatsDO::getHour,
                        Collectors.summingInt(LinkAccessStatsDO::getPv)));
        List<Integer> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) result.add(hourMap.getOrDefault(h, 0));
        return result;
    }

    /**
     * 构建周一~周日 PV 统计列表
     */
    private List<Integer> buildWeekdayStats(List<LinkAccessStatsDO> weekdayData) {
        Map<Integer, Integer> weekdayMap = weekdayData.stream()
                .collect(Collectors.groupingBy(LinkAccessStatsDO::getWeekday,
                        Collectors.summingInt(LinkAccessStatsDO::getPv)));
        List<Integer> result = new ArrayList<>();
        for (int i = 1; i < 8; i++) result.add(weekdayMap.getOrDefault(i, 0));
        return result;
    }

    /**
     * 构建高频访问 IP 统计
     */
    private List<ShortLinkStatsTopIpRespDTO> buildTopIpStats(List<HashMap<String, Object>> data) {
        return data.stream().map(each -> ShortLinkStatsTopIpRespDTO.builder()
                .ip(each.get(COL_IP).toString())
                .cnt(Integer.parseInt(each.get(COL_COUNT).toString())).build()).toList();
    }

    /**
     * 构建浏览器维度统计（含占比）
     */
    private List<ShortLinkStatsBrowserRespDTO> buildBrowserStats(List<HashMap<String, Object>> data) {
        int sum = data.stream().mapToInt(e -> Integer.parseInt(e.get(COL_COUNT).toString())).sum();
        return data.stream().map(each -> {
            int cnt = Integer.parseInt(each.get(COL_COUNT).toString());
            return ShortLinkStatsBrowserRespDTO.builder()
                    .cnt(cnt).browser(each.get(COL_BROWSER).toString())
                    .ratio(Math.round((double) cnt / sum * 100.0) / 100.0).build();
        }).toList();
    }

    /**
     * 构建操作系统维度统计（含占比）
     */
    private List<ShortLinkStatsOsRespDTO> buildOsStats(List<HashMap<String, Object>> data) {
        int sum = data.stream().mapToInt(e -> Integer.parseInt(e.get(COL_COUNT).toString())).sum();
        return data.stream().map(each -> {
            int cnt = Integer.parseInt(each.get(COL_COUNT).toString());
            return ShortLinkStatsOsRespDTO.builder()
                    .cnt(cnt).os(each.get(COL_OS).toString())
                    .ratio(Math.round((double) cnt / sum * 100.0) / 100.0).build();
        }).toList();
    }

    /**
     * 构建新旧访客类型统计（含占比）
     */
    private List<ShortLinkStatsUvRespDTO> buildUvTypeStats(HashMap<String, Object> data) {
        int oldCnt = Integer.parseInt(data.get(COL_OLD_USER_CNT).toString());
        int newCnt = Integer.parseInt(data.get(COL_NEW_USER_CNT).toString());
        int total = oldCnt + newCnt;
        return List.of(
                ShortLinkStatsUvRespDTO.builder().uvType(UV_TYPE_NEW).cnt(newCnt)
                        .ratio(Math.round((double) newCnt / total * 100.0) / 100.0).build(),
                ShortLinkStatsUvRespDTO.builder().uvType(UV_TYPE_OLD).cnt(oldCnt)
                        .ratio(Math.round((double) oldCnt / total * 100.0) / 100.0).build());
    }

    /**
     * 构建设备类型维度统计（含占比）
     */
    private List<ShortLinkStatsDeviceRespDTO> buildDeviceStats(List<LinkDeviceStatsDO> data) {
        int sum = data.stream().mapToInt(LinkDeviceStatsDO::getCnt).sum();
        return data.stream().map(each -> ShortLinkStatsDeviceRespDTO.builder()
                .cnt(each.getCnt()).device(each.getDevice())
                .ratio(Math.round((double) each.getCnt() / sum * 100.0) / 100.0).build()).toList();
    }

    /**
     * 构建网络类型维度统计（含占比）
     */
    private List<ShortLinkStatsNetworkRespDTO> buildNetworkStats(List<LinkNetworkStatsDO> data) {
        int sum = data.stream().mapToInt(LinkNetworkStatsDO::getCnt).sum();
        return data.stream().map(each -> ShortLinkStatsNetworkRespDTO.builder()
                .cnt(each.getCnt()).network(each.getNetwork())
                .ratio(Math.round((double) each.getCnt() / sum * 100.0) / 100.0).build()).toList();
    }
}
