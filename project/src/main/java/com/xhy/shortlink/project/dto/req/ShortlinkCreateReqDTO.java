package com.xhy.shortlink.project.dto.req;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/*
* 创建短链接请求参数
* */
@Data
public class ShortlinkCreateReqDTO {

    /*
    * 域名
    * */
    private String domain;

    /*
    * 原始连接
    * */
    private String originUrl;

    /*
    * 分组标识
    * */
    private String gid;

    /*
    * 创建类型
    * */
    private Integer createType;

    /*
    * 有效期类型
    * */
    private Integer validDateType;

    /*
    * 有效期
    * */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;

    /*
    * 短链接描述
    * */
    @TableField("`describe`")
    private String describe;
}
