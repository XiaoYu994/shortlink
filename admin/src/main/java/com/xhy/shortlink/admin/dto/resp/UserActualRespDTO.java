package com.xhy.shortlink.admin.dto.resp;

import lombok.Data;

import java.util.Date;

/*
* 用户不脱敏信息接口返回响应
* */
@Data
public class UserActualRespDTO {
    /**
     * ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;


    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String mail;

    /**
     * 注销时间戳
     */
    private Long deletionTime;

    /**
     * 创建时间
     */
    private Date createTime;
}
