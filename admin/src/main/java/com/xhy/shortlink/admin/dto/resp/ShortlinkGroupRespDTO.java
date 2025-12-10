package com.xhy.shortlink.admin.dto.resp;

import lombok.Data;


/*
* 查询分组信息返回对象
* */
@Data
public class ShortlinkGroupRespDTO {
    /**
     * 分组标识
     */
    private String gid;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 分组排序
     */
    private Integer sortOrder;
}
