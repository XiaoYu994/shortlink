package com.xhy.shortlink.admin.remote.dto.req;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/*
* 分组短链接监控日志请求参数
* */
@Data
public class ShortLinkStatsAccessRecordGroupReqDTO extends Page {


    /**
     * 分组标识
     */
    private String gid;

    /**
     * 开始日期
     */
    private String startDate;

    /**
     * 结束日期
     */
    private String endDate;

}
