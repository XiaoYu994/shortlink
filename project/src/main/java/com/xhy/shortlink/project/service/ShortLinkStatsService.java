package com.xhy.shortlink.project.service;

import com.xhy.shortlink.project.dto.req.ShortLinkStatsReqDTO;
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
}
