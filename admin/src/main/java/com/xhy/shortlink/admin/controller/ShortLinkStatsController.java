package com.xhy.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.remote.ShortLinkRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkStatsReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkStatsRespDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * 短链接统计接口
 * */
@RestController
public class ShortLinkStatsController {

    /*
     * TODO 后续重构为SpringCloud Feign调用
     * */
    ShortLinkRemoteService shortlinkRemoteService = new ShortLinkRemoteService(){
    };

    /*
    * 单个短链接监控统计
    * */
    @GetMapping("/api/short-link/admin/v1/stats")
    public Result<ShortLinkStatsRespDTO> shortLinkStats(ShortLinkStatsReqDTO requestParam) {
        return shortlinkRemoteService.oneShortLinkStats(requestParam);
    }

    /*
     * 访问单个短链接指定时间内访问记录监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/access-record")
    public Result<IPage<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam) {
        return shortlinkRemoteService.shortLinkStatsAccessRecord(requestParam);
    }

}
