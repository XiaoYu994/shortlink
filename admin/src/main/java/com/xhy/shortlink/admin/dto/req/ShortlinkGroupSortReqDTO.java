package com.xhy.shortlink.admin.dto.req;

import lombok.Data;

/*
* 短链接分组排序请求参数
* */
@Data
public class ShortlinkGroupSortReqDTO {

    /*
    * 排序号
    * */
    private Integer sortOrder;
    /*
    * 排序标识
    * */
    private String gid;
}
