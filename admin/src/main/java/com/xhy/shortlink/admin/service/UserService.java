package com.xhy.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.admin.dao.entity.UserDO;
import com.xhy.shortlink.admin.dto.req.UserLoginReqDTO;
import com.xhy.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.xhy.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.xhy.shortlink.admin.dto.resp.UserRespDTO;

/*
 * 用户接口层
 */
public interface UserService extends IService<UserDO> {

    /**
     * 根据用户名查询用户信息
     * @param username
     * @return 用户返回实体
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 判断用户名是否存在
     * @param username
     * @return 存在返回true 不存在返回false
     */
    Boolean hasUsername(String username);

    /**
     * 用户注册
     *
     * @param requestParam 注册用户参数
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 根据用户名修改用户信息
     *
     * @param requestParam 修改用户参数
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * 用户登录
     *
     * @param requestParam 登录用户参数
     * @return 登录成功返回token
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);


    /**
     * 验证用户登录
     *
     * @param username 用户名
     * @param token    用户token
     * @return 登录成功返回true 登录失败返回false
     */
    Boolean checkLogin(String username, String token);

    /**
     * 用户登出
     *
     * @param username 用户名
     * @param token    用户token
     */
    Boolean logout(String username, String token);
}
