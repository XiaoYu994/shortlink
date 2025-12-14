package com.xhy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.project.dao.entity.*;
import com.xhy.shortlink.project.dao.mapper.*;
import com.xhy.shortlink.project.dto.req.*;
import com.xhy.shortlink.project.dto.resp.*;
import com.xhy.shortlink.project.service.ShortLinkStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/*
* 短链接统计服务实现层
* */
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        // 查询基础数据统计
        final List<LinkAccessStatsDO> linkAccessStatsDOList = linkAccessStatsMapper.listStatsByShortLink(requestParam);
        if (CollUtil.isEmpty(linkAccessStatsDOList)) {
            return null;
        }
        // 短链接获取指定日期内PV、UV、UIP数据
        LinkAccessStatsDO pvUvUidStatsByShortLink = linkAccessLogsMapper.findPvUvUidStatsByShortLink(requestParam);
        // 获取监控地区
        List<ShortLinkStatsLocaleCNRespDTO> localeCnStats = new ArrayList<>();
        final List<LinkLocaleStatsDO> linkLocaleStatsDOList = linkLocaleStatsMapper.listLocaleByShortLink(requestParam);
        int localCntSum = linkLocaleStatsDOList.stream().mapToInt(LinkLocaleStatsDO::getCnt).sum();
        linkLocaleStatsDOList.forEach(each -> {
            localeCnStats.add(ShortLinkStatsLocaleCNRespDTO.builder()
                    .cnt(each.getCnt())
                    .locale(each.getProvince())
                    .ratio(Math.round(each.getCnt() * 1.0 / localCntSum * 100) / 100.0)
                    .build());
        });
        // 小时访问量
        final List<Integer> hourStats = new ArrayList<>();
        final List<LinkAccessStatsDO> listHourStatsByShortLink = linkAccessStatsMapper.listHourStatsByShortLink(requestParam);
        Map<Integer, Integer> hourPVMap = listHourStatsByShortLink.stream()
                .collect(Collectors.groupingBy(LinkAccessStatsDO::getHour,
                Collectors.summingInt(LinkAccessStatsDO::getPv)));
        // 0~23 点补齐
        for (int h = 0; h < 24; h++) {
            hourStats.add(hourPVMap.getOrDefault(h, 0));
        }
        // 高频访问ip
        final List<ShortLinkStatsTopIpRespDTO> topIpStats = new ArrayList<>();
        final List<HashMap<String,Object>> listTopIpByShortLink = linkAccessLogsMapper.listTopIpByShortLink(requestParam);
        listTopIpByShortLink.forEach(each -> {
            topIpStats.add(ShortLinkStatsTopIpRespDTO.builder()
                    .ip(each.get("ip").toString())
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .build());
        });
        // 一周访问详情
        List<Integer> weekdayStats = new ArrayList<>();
        List<LinkAccessStatsDO> listWeekdayStatsByShortLink = linkAccessStatsMapper.listWeekdayStatsByShortLink(requestParam);
        Map<Integer, Integer> weekdayPVMap = listWeekdayStatsByShortLink.stream()
                .collect(Collectors.groupingBy(LinkAccessStatsDO::getWeekday,
                        Collectors.summingInt(LinkAccessStatsDO::getPv)));
        for (int i = 1; i < 8; i++) {
            weekdayStats.add(weekdayPVMap.getOrDefault(i, 0));
        }
        // 浏览器访问详情
        List<ShortLinkStatsBrowserRespDTO> browserStats = new ArrayList<>();
        List<HashMap<String, Object>> listBrowserStatsByShortLink = linkBrowserStatsMapper.listBrowserStatsByShortLink(requestParam);
        int browserSum = listBrowserStatsByShortLink.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        listBrowserStatsByShortLink.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / browserSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsBrowserRespDTO browserRespDTO = ShortLinkStatsBrowserRespDTO.builder()
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .browser(each.get("browser").toString())
                    .ratio(actualRatio)
                    .build();
            browserStats.add(browserRespDTO);
        });
        // 操作系统访问详情
        List<ShortLinkStatsOsRespDTO> osStats = new ArrayList<>();
        List<HashMap<String, Object>> listOsStatsByShortLink = linkOsStatsMapper.listOsStatsByShortLink(requestParam);
        int osSum = listOsStatsByShortLink.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        listOsStatsByShortLink.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / osSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsOsRespDTO osRespDTO = ShortLinkStatsOsRespDTO.builder()
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .os(each.get("os").toString())
                    .ratio(actualRatio)
                    .build();
            osStats.add(osRespDTO);
        });
        // 访客访问类型详情
        List<ShortLinkStatsUvRespDTO> uvTypeStats = new ArrayList<>();
        HashMap<String, Object> findUvTypeByShortLink = linkAccessLogsMapper.findUvTypeCntByShortLink(requestParam);
        int oldUserCnt = Integer.parseInt(findUvTypeByShortLink.get("oldUserCnt").toString());
        int newUserCnt = Integer.parseInt(findUvTypeByShortLink.get("newUserCnt").toString());
        int uvSum = oldUserCnt + newUserCnt;
        double oldRatio = (double) oldUserCnt / uvSum;
        double actualOldRatio = Math.round(oldRatio * 100.0) / 100.0;
        double newRatio = (double) newUserCnt / uvSum;
        double actualNewRatio = Math.round(newRatio * 100.0) / 100.0;
        ShortLinkStatsUvRespDTO newUvRespDTO = ShortLinkStatsUvRespDTO.builder()
                .uvType("newUser")
                .cnt(newUserCnt)
                .ratio(actualNewRatio)
                .build();
        uvTypeStats.add(newUvRespDTO);
        ShortLinkStatsUvRespDTO oldUvRespDTO = ShortLinkStatsUvRespDTO.builder()
                .uvType("oldUser")
                .cnt(oldUserCnt)
                .ratio(actualOldRatio)
                .build();
        uvTypeStats.add(oldUvRespDTO);
        // 访问设备类型详情
        List<ShortLinkStatsDeviceRespDTO> deviceStats = new ArrayList<>();
        List<LinkDeviceStatsDO> listDeviceStatsByShortLink = linkDeviceStatsMapper.listDeviceStatsByShortLink(requestParam);
        int deviceSum = listDeviceStatsByShortLink.stream()
                .mapToInt(LinkDeviceStatsDO::getCnt)
                .sum();
        listDeviceStatsByShortLink.forEach(each -> {
            double ratio = (double) each.getCnt() / deviceSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsDeviceRespDTO deviceRespDTO = ShortLinkStatsDeviceRespDTO.builder()
                    .cnt(each.getCnt())
                    .device(each.getDevice())
                    .ratio(actualRatio)
                    .build();
            deviceStats.add(deviceRespDTO);
        });
        // 访问网络类型详情
        List<ShortLinkStatsNetworkRespDTO> networkStats = new ArrayList<>();
        List<LinkNetworkStatsDO> listNetworkStatsByShortLink = linkNetworkStatsMapper.listNetworkStatsByShortLink(requestParam);
        int networkSum = listNetworkStatsByShortLink.stream()
                .mapToInt(LinkNetworkStatsDO::getCnt)
                .sum();
        listNetworkStatsByShortLink.forEach(each -> {
            double ratio = (double) each.getCnt() / networkSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsNetworkRespDTO networkRespDTO = ShortLinkStatsNetworkRespDTO.builder()
                    .cnt(each.getCnt())
                    .network(each.getNetwork())
                    .ratio(actualRatio)
                    .build();
            networkStats.add(networkRespDTO);
        });
        return ShortLinkStatsRespDTO.builder()
                .uip(pvUvUidStatsByShortLink.getUip())
                .pv(pvUvUidStatsByShortLink.getPv())
                .uv(pvUvUidStatsByShortLink.getUv())
                .daily(BeanUtil.copyToList(linkAccessStatsDOList, ShortLinkStatsAccessDailyRespDTO.class))
                .localeCnStats(localeCnStats)
                .hourStats(hourStats)
                .topIpStats(topIpStats)
                .weekdayStats(weekdayStats)
                .browserStats(browserStats)
                .osStats(osStats)
                .uvTypeStats(uvTypeStats)
                .deviceStats(deviceStats)
                .networkStats(networkStats)
                .build();

    }

    @Override
    public ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkStatsGroupReqDTO requestParam) {
        // 查询基础数据统计
        final List<LinkAccessStatsDO> linkAccessStatsDOList = linkAccessStatsMapper.listStatsByShortLinkGroup(requestParam);
        if (CollUtil.isEmpty(linkAccessStatsDOList)) {
            return null;
        }
        // 短链接获取指定日期内PV、UV、UIP数据
        LinkAccessStatsDO pvUvUidStatsByShortLink = linkAccessLogsMapper.findPvUvUidStatsByShortLinkGroup(requestParam);
        // 获取监控地区
        List<ShortLinkStatsLocaleCNRespDTO> localeCnStats = new ArrayList<>();
        final List<LinkLocaleStatsDO> linkLocaleStatsDOList = linkLocaleStatsMapper.listLocaleByShortLinkGroup(requestParam);
        int localCntSum = linkLocaleStatsDOList.stream().mapToInt(LinkLocaleStatsDO::getCnt).sum();
        linkLocaleStatsDOList.forEach(each -> {
            localeCnStats.add(ShortLinkStatsLocaleCNRespDTO.builder()
                    .cnt(each.getCnt())
                    .locale(each.getProvince())
                    .ratio(Math.round(each.getCnt() * 1.0 / localCntSum * 100) / 100.0)
                    .build());
        });
        // 小时访问量
        final List<Integer> hourStats = new ArrayList<>();
        final List<LinkAccessStatsDO> listHourStatsByShortLink = linkAccessStatsMapper.listHourStatsByShortLinkGroup(requestParam);
        Map<Integer, Integer> hourPVMap = listHourStatsByShortLink.stream()
                .collect(Collectors.groupingBy(LinkAccessStatsDO::getHour,
                        Collectors.summingInt(LinkAccessStatsDO::getPv)));
        // 0~23 点补齐
        for (int h = 0; h < 24; h++) {
            hourStats.add(hourPVMap.getOrDefault(h, 0));
        }
        // 高频访问ip
        final List<ShortLinkStatsTopIpRespDTO> topIpStats = new ArrayList<>();
        final List<HashMap<String,Object>> listTopIpByShortLink = linkAccessLogsMapper.listTopIpByShortLinkGroup(requestParam);
        listTopIpByShortLink.forEach(each -> {
            topIpStats.add(ShortLinkStatsTopIpRespDTO.builder()
                    .ip(each.get("ip").toString())
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .build());
        });
        // 一周访问详情
        List<Integer> weekdayStats = new ArrayList<>();
        List<LinkAccessStatsDO> listWeekdayStatsByShortLink = linkAccessStatsMapper.listWeekdayStatsByShortLinkGroup(requestParam);
        Map<Integer, Integer> weekdayPVMap = listWeekdayStatsByShortLink.stream()
                .collect(Collectors.groupingBy(LinkAccessStatsDO::getWeekday,
                        Collectors.summingInt(LinkAccessStatsDO::getPv)));
        for (int i = 1; i < 8; i++) {
            weekdayStats.add(weekdayPVMap.getOrDefault(i, 0));
        }
        // 浏览器访问详情
        List<ShortLinkStatsBrowserRespDTO> browserStats = new ArrayList<>();
        List<HashMap<String, Object>> listBrowserStatsByShortLink = linkBrowserStatsMapper.listBrowserStatsByShortLinkGroup(requestParam);
        int browserSum = listBrowserStatsByShortLink.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        listBrowserStatsByShortLink.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / browserSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsBrowserRespDTO browserRespDTO = ShortLinkStatsBrowserRespDTO.builder()
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .browser(each.get("browser").toString())
                    .ratio(actualRatio)
                    .build();
            browserStats.add(browserRespDTO);
        });
        // 操作系统访问详情
        List<ShortLinkStatsOsRespDTO> osStats = new ArrayList<>();
        List<HashMap<String, Object>> listOsStatsByShortLink = linkOsStatsMapper.listOsStatsByShortLinkGroup(requestParam);
        int osSum = listOsStatsByShortLink.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        listOsStatsByShortLink.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / osSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsOsRespDTO osRespDTO = ShortLinkStatsOsRespDTO.builder()
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .os(each.get("os").toString())
                    .ratio(actualRatio)
                    .build();
            osStats.add(osRespDTO);
        });
        // 访客访问类型详情
        List<ShortLinkStatsUvRespDTO> uvTypeStats = new ArrayList<>();
        HashMap<String, Object> findUvTypeByShortLink = linkAccessLogsMapper.findUvTypeCntByShortLinkGroup(requestParam);
        int oldUserCnt = Integer.parseInt(findUvTypeByShortLink.get("oldUserCnt").toString());
        int newUserCnt = Integer.parseInt(findUvTypeByShortLink.get("newUserCnt").toString());
        int uvSum = oldUserCnt + newUserCnt;
        double oldRatio = (double) oldUserCnt / uvSum;
        double actualOldRatio = Math.round(oldRatio * 100.0) / 100.0;
        double newRatio = (double) newUserCnt / uvSum;
        double actualNewRatio = Math.round(newRatio * 100.0) / 100.0;
        ShortLinkStatsUvRespDTO newUvRespDTO = ShortLinkStatsUvRespDTO.builder()
                .uvType("newUser")
                .cnt(newUserCnt)
                .ratio(actualNewRatio)
                .build();
        uvTypeStats.add(newUvRespDTO);
        ShortLinkStatsUvRespDTO oldUvRespDTO = ShortLinkStatsUvRespDTO.builder()
                .uvType("oldUser")
                .cnt(oldUserCnt)
                .ratio(actualOldRatio)
                .build();
        uvTypeStats.add(oldUvRespDTO);
        // 访问设备类型详情
        List<ShortLinkStatsDeviceRespDTO> deviceStats = new ArrayList<>();
        List<LinkDeviceStatsDO> listDeviceStatsByShortLink = linkDeviceStatsMapper.listDeviceStatsByShortLinkGroup(requestParam);
        int deviceSum = listDeviceStatsByShortLink.stream()
                .mapToInt(LinkDeviceStatsDO::getCnt)
                .sum();
        listDeviceStatsByShortLink.forEach(each -> {
            double ratio = (double) each.getCnt() / deviceSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsDeviceRespDTO deviceRespDTO = ShortLinkStatsDeviceRespDTO.builder()
                    .cnt(each.getCnt())
                    .device(each.getDevice())
                    .ratio(actualRatio)
                    .build();
            deviceStats.add(deviceRespDTO);
        });
        // 访问网络类型详情
        List<ShortLinkStatsNetworkRespDTO> networkStats = new ArrayList<>();
        List<LinkNetworkStatsDO> listNetworkStatsByShortLink = linkNetworkStatsMapper.listNetworkStatsByShortLinkGroup(requestParam);
        int networkSum = listNetworkStatsByShortLink.stream()
                .mapToInt(LinkNetworkStatsDO::getCnt)
                .sum();
        listNetworkStatsByShortLink.forEach(each -> {
            double ratio = (double) each.getCnt() / networkSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsNetworkRespDTO networkRespDTO = ShortLinkStatsNetworkRespDTO.builder()
                    .cnt(each.getCnt())
                    .network(each.getNetwork())
                    .ratio(actualRatio)
                    .build();
            networkStats.add(networkRespDTO);
        });
        return ShortLinkStatsRespDTO.builder()
                .uip(pvUvUidStatsByShortLink.getUip())
                .pv(pvUvUidStatsByShortLink.getPv())
                .uv(pvUvUidStatsByShortLink.getUv())
                .daily(BeanUtil.copyToList(linkAccessStatsDOList, ShortLinkStatsAccessDailyRespDTO.class))
                .localeCnStats(localeCnStats)
                .hourStats(hourStats)
                .topIpStats(topIpStats)
                .weekdayStats(weekdayStats)
                .browserStats(browserStats)
                .osStats(osStats)
                .uvTypeStats(uvTypeStats)
                .deviceStats(deviceStats)
                .networkStats(networkStats)
                .build();
    }

    @Override
    public IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam) {
        // 查询访问记录
        final LambdaQueryWrapper<LinkAccessLogsDO> queryWrapper = Wrappers.lambdaQuery(LinkAccessLogsDO.class)
                .eq(LinkAccessLogsDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(LinkAccessLogsDO::getGid, requestParam.getGid())
                .between(LinkAccessLogsDO::getCreateTime, requestParam.getStartDate(), requestParam.getEndDate())
                .orderByDesc(LinkAccessLogsDO::getCreateTime);
         IPage<LinkAccessLogsDO> linkAccessLogsDOIPage = linkAccessLogsMapper.selectPage(requestParam, queryWrapper);
         if(CollUtil.isEmpty(linkAccessLogsDOIPage.getRecords())) {
                return new Page<>();
         }
         // 转换为响应参数
        final IPage<ShortLinkStatsAccessRecordRespDTO> actualResult = linkAccessLogsDOIPage.convert(each -> BeanUtil.toBean(each, ShortLinkStatsAccessRecordRespDTO.class));
        final List<String> usetAccessLogsList = actualResult.getRecords().stream().map(ShortLinkStatsAccessRecordRespDTO::getUser).toList();
        // 获取访客访问类型
        List<Map<String, Object>> uvTypeList = linkAccessLogsMapper.selectUvTypeByUser(ShortLinkUvTypeReqDTO.builder()
                        .fullShortUrl(requestParam.getFullShortUrl())
                        .gid(requestParam.getGid())
                        .enableStatus(requestParam.getEnableStatus())
                        .startDate(requestParam.getStartDate())
                        .endDate(requestParam.getEndDate())
                        .userAccessLogsList(usetAccessLogsList)
                .build());

        // 填充uvType到返回结果
        actualResult.getRecords().forEach(each -> {
            each.setUvType(uvTypeList.stream()
                    .filter(uv -> Objects.equals(each.getUser(), uv.get("user")))
                    .findFirst()
                    .map(uv -> uv.get("uvType").toString())
                    .orElse("旧访客"));
        });
        return actualResult;
    }

    @Override
    public IPage<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(ShortLinkStatsAccessRecordGroupReqDTO requestParam) {
        // 查询访问记录
        final LambdaQueryWrapper<LinkAccessLogsDO> queryWrapper = Wrappers.lambdaQuery(LinkAccessLogsDO.class)
                .eq(LinkAccessLogsDO::getGid, requestParam.getGid())
                .between(LinkAccessLogsDO::getCreateTime, requestParam.getStartDate(), requestParam.getEndDate())
                .orderByDesc(LinkAccessLogsDO::getCreateTime);
        IPage<LinkAccessLogsDO> linkAccessLogsDOIPage = linkAccessLogsMapper.selectPage(requestParam, queryWrapper);
        if(CollUtil.isEmpty(linkAccessLogsDOIPage.getRecords())) {
            return new Page<>();
        }
        // 转换为响应参数
        final IPage<ShortLinkStatsAccessRecordRespDTO> actualResult = linkAccessLogsDOIPage.convert(each -> BeanUtil.toBean(each, ShortLinkStatsAccessRecordRespDTO.class));
        final List<String> usetAccessLogsList = actualResult.getRecords().stream().map(ShortLinkStatsAccessRecordRespDTO::getUser).toList();
        // 获取访客访问类型
        List<Map<String, Object>> uvTypeList = linkAccessLogsMapper.selectUvTypeByUserGruop(ShortLinkUvTypeReqDTO.builder()
                .gid(requestParam.getGid())
                .startDate(requestParam.getStartDate())
                .endDate(requestParam.getEndDate())
                .userAccessLogsList(usetAccessLogsList)
                .build());

        // 填充uvType到返回结果
        actualResult.getRecords().forEach(each -> {
            each.setUvType(uvTypeList.stream()
                    .filter(uv -> Objects.equals(each.getUser(), uv.get("user")))
                    .findFirst()
                    .map(uv -> uv.get("uvType").toString())
                    .orElse("旧访客"));
        });
        return actualResult;
    }

}
