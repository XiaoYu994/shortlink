package com.xhy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsAccessRecordGroupReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsGroupReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkStatsRespDTO;

/*
* 短链接监控服务层
* */
public interface ShortLinkStatsService {

    /**
     * 获取单个短链接监控数据
     *
     * @param requestParam 获取短链接监控数据入参
     * @return 短链接监控数据
     */
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);


    /**
     * 获取分组短链接监控数据
     *
     * @param requestParam 获取短链接监控数据分组入参
     * @return 短链接监控数据
     */
    ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkStatsGroupReqDTO requestParam);

    /**
     * 获取单个短链接日志监控数据
     *
     * @param requestParam 获取短链接监控日志数据入参
     * @return 短链接监控日志数据
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam);

    /**
     * 获取分组短链接日志监控数据
     *
     * @param requestParam 获取短链接监控日志数据入参
     * @return 短链接监控日志数据
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(ShortLinkStatsAccessRecordGroupReqDTO requestParam);
}
