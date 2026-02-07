/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xhy.shortlink.biz.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.biz.userservice.dao.entity.UserDO;
import com.xhy.shortlink.biz.userservice.dto.req.UserLoginReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.UserUpdateReqDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserLoginRespDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserRespDTO;

/**
 * 用户接口层
 */
public interface UserService extends IService<UserDO> {

    /**
     * 根据用户名查询用户信息
     * @param username 用户名
     * @return 用户返回实体
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 检查用户名是否可用
     * @param username 用户名
     * @return 用户名已存在返回true，不存在返回false
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
    void updateUser(UserUpdateReqDTO requestParam);

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
     * @param token 用户token
     * @return 登录成功返回true 登录失败返回false
     */
    Boolean checkLogin(String token);

    /**
     * 用户登出
     *
     * @param token 用户token
     */
    void logout(String token);
}
