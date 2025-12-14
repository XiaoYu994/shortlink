package com.xhy.shortlink.project.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/*
* 查询短链接访客类型请求参数
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkUvTypeReqDTO {

    /*
    * 完整短链接
    * */
    private String fullShortUrl;
    /*
    * 分组标识
    * */
    private String gid;
    /*
    * 短链接启用标识
    * */
    private Integer enableStatus;
    /*
    * 开始时间
    * */
    private String startDate;
    /*
    * 结束时间
    * */
    private String endDate;
    /*
    * 用户访问集合
    * */
    private List<String> userAccessLogsList;

}
