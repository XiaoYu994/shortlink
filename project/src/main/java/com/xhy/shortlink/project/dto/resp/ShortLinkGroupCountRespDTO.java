package com.xhy.shortlink.project.dto.resp;

import lombok.Data;

/*
* 短链接分组数量查询响应参数
* */
@Data
public class ShortLinkGroupCountRespDTO {
    /*
    * 分组id
    * */
    private String gid;
    /*
    * 短链接数量
    * */
    private Integer shortLinkCount;
}
