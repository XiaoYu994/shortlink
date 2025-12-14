package com.xhy.shortlink.project.dto.req;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.project.dao.entity.LinkAccessLogsDO;
import lombok.Data;

/*
* 分组短链接监控日志请求参数
* */
@Data
public class ShortLinkStatsAccessRecordGroupReqDTO extends Page<LinkAccessLogsDO> {


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
