package com.xhy.shortlink.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.common.convention.result.Results;
import com.xhy.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.xhy.shortlink.admin.dto.resp.UserActualRespDTO;
import com.xhy.shortlink.admin.dto.resp.UserRespDTO;
import com.xhy.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/api/short-link/v1/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username) {
        return Results.success(userService.getUserByUsername(username));
    }

    /*
     * 根据有用户名查询用户信息
     * */
    @GetMapping("/api/short-link/v1/actual/user/{username}")
    public Result<UserActualRespDTO> getActualUserByUsername(@PathVariable("username") String username) {
        return Results.success(BeanUtil.toBean(userService.getUserByUsername(username), UserActualRespDTO.class));
    }

    /*
    * 检查用户名是否可用
    * */
    @GetMapping("/api/short-link/v1/user/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") String username) {
        return Results.success(userService.hasUsername( username));
    }


    /*
    * 用户注册
    * */
    @PostMapping("/api/short-link/v1/user/register")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam) {
        userService.register(requestParam);
        return Results.success();
    }

    /*
    * 修改用户
    * */
    @PutMapping("/api/short-link/admin/v1/user")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam) {
        userService.update(requestParam);
        return Results.success();
    }

}
