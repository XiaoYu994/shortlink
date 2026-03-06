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

import com.xhy.shortlink.biz.userservice.dto.req.UserLoginReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.UserUpdateReqDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserLoginRespDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户服务单元测试
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void testHasUsername() {
        // 测试用户名是否存在
        String existingUsername = "testuser";
        String nonExistingUsername = "nonexistuser_" + System.currentTimeMillis();

        // 不存在的用户名应该返回 false
        Boolean result = userService.hasUsername(nonExistingUsername);
        assertFalse(result, "新用户名应该不存在");
    }

    @Test
    void testRegisterAndLogin() {
        // 生成唯一的测试用户名
        String username = "testuser_" + System.currentTimeMillis();
        String password = "Test123456";
        String realName = "测试用户";
        String phone = "13800138000";
        String mail = "test@example.com";

        // 1. 测试用户注册
        UserRegisterReqDTO registerReq = new UserRegisterReqDTO();
        registerReq.setUsername(username);
        registerReq.setPassword(password);
        registerReq.setRealName(realName);
        registerReq.setPhone(phone);
        registerReq.setMail(mail);

        assertDoesNotThrow(() -> userService.register(registerReq), "用户注册应该成功");

        // 2. 验证用户名已存在
        Boolean hasUsername = userService.hasUsername(username);
        assertTrue(hasUsername, "注册后用户名应该存在");

        // 注意：登录测试需要 HTTP 上下文（Sa-Token），在单元测试中跳过
        // 如需测试登录功能，请使用集成测试或 @WebMvcTest
    }

    @Test
    void testGetUserByUsername() {
        // 生成唯一的测试用户名
        String username = "queryuser_" + System.currentTimeMillis();
        String password = "Test123456";

        // 先注册一个用户
        UserRegisterReqDTO registerReq = new UserRegisterReqDTO();
        registerReq.setUsername(username);
        registerReq.setPassword(password);
        registerReq.setRealName("查询测试用户");
        registerReq.setPhone("13900139000");
        registerReq.setMail("query@example.com");

        userService.register(registerReq);

        // 查询用户信息
        UserRespDTO userResp = userService.getUserByUsername(username);
        assertNotNull(userResp, "用户信息不应为空");
        assertEquals(username, userResp.getUsername(), "用户名应该匹配");
        assertEquals("查询测试用户", userResp.getRealName(), "真实姓名应该匹配");
    }

    @Test
    void testUpdateUser() {
        // 生成唯一的测试用户名
        String username = "updateuser_" + System.currentTimeMillis();
        String password = "Test123456";

        // 先注册一个用户
        UserRegisterReqDTO registerReq = new UserRegisterReqDTO();
        registerReq.setUsername(username);
        registerReq.setPassword(password);
        registerReq.setRealName("更新前姓名");
        registerReq.setPhone("13700137000");
        registerReq.setMail("update@example.com");

        userService.register(registerReq);

        // 更新用户信息
        UserUpdateReqDTO updateReq = new UserUpdateReqDTO();
        updateReq.setUsername(username);
        updateReq.setRealName("更新后姓名");
        updateReq.setPhone("13700137001");
        updateReq.setMail("updated@example.com");

        assertDoesNotThrow(() -> userService.updateUser(updateReq), "用户更新应该成功");

        // 验证更新结果
        UserRespDTO userResp = userService.getUserByUsername(username);
        assertEquals("更新后姓名", userResp.getRealName(), "真实姓名应该已更新");
    }
}
