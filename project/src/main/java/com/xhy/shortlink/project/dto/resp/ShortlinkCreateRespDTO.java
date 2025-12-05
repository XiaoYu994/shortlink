package com.xhy.shortlink.project.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
* 创建短链接返回响应
* */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortlinkCreateRespDTO {


    /*
    * 原始连接
    * */
    private String originUrl;

    /*
    * 分组标识
    * */
    private String gid;


    /*
     * 短链接
     * */
    private String fullShortUrl;
}
