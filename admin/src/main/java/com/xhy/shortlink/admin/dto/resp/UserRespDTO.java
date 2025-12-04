package com.xhy.shortlink.admin.dto.resp;


import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xhy.shortlink.admin.common.serialize.PhoneDesensitizationSerializer;
import lombok.Data;

import java.util.Date;

/*
* 查询用户接口返回响应 脱敏展示
* */
@Data
public class UserRespDTO {


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
    @JsonSerialize(using = PhoneDesensitizationSerializer.class)
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
