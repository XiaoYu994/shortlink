package com.xhy.shortlink.project.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
* 短链接分组数量查询响应参数
* */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkGroupCountRespDTO {
    /*
    * 分组id
    * */
    private String gid;
    /*
    * 短链接数量
    * */
    private Long shortLinkCount;
}
