package com.xhy.shortlink.admin.dto.req;

import lombok.Data;

/*
* 创建短链接分组请求参数
* */
@Data
public class ShortlinkGroupAddReqDTO {
    /*
    * 分组名称
    * */
    private String name;
}
