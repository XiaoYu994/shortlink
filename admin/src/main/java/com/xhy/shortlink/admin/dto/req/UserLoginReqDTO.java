package com.xhy.shortlink.admin.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/*
* 用户登录请求对象
* */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserLoginReqDTO {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

}
