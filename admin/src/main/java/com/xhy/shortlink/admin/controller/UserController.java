package com.xhy.shortlink.admin.controller;

import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.common.convention.result.Results;
import com.xhy.shortlink.admin.dto.resp.UserRespDTO;
import com.xhy.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @desc: 用户管理控制层
 * @Author:  XiaoYu
 * @date:  2025/12/2 15:57
*/
@RestController
@RequiredArgsConstructor
public class UserController {

    // 构造器注入
    private final UserService userService;
    /*
    * 根据有用户名查询用户信息
    * */
    @GetMapping("/api/shortlink/v1/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username) {

        return Results.success(userService.getUserByUsername(username));
    }
}
