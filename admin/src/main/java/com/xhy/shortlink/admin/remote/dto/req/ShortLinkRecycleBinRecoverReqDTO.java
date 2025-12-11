package com.xhy.shortlink.admin.remote.dto.req;

import lombok.Data;

/*
* 恢复短链接请求参数
* */
@Data
public class ShortLinkRecycleBinRecoverReqDTO {

    /*
    * 分组标识
    * */
    private String gid;

    /*
    * 短链接
    * */
    private String fullShortUrl;
}
