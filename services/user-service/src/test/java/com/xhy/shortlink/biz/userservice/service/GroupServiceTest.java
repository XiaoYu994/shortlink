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

import com.xhy.shortlink.biz.userservice.dto.req.ShortlinkGroupSortReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.ShortlinkGroupUpdateReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.ShortlinkGroupRespDTO;
import com.xhy.shortlink.biz.userservice.remote.ShortLinkRemoteService;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import com.xhy.shortlink.framework.starter.user.core.UserInfoDTO;
import com.xhy.shortlink.framework.starter.web.Results;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 分组服务单元测试
 */
@SpringBootTest
@ActiveProfiles("test")
class GroupServiceTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserService userService;

    @MockBean
    private ShortLinkRemoteService shortLinkRemoteService;

    private String testUsername;

    @BeforeEach
    void setUp() {
        // 为每个测试创建一个唯一的测试用户
        testUsername = "grouptest_" + System.currentTimeMillis();
        UserRegisterReqDTO registerReq = new UserRegisterReqDTO();
        registerReq.setUsername(testUsername);
        registerReq.setPassword("Test123456");
        registerReq.setRealName("分组测试用户");
        registerReq.setPhone("13600136000");
        registerReq.setMail("grouptest@example.com");

        userService.register(registerReq);

        // 设置用户上下文，供 GroupService 使用
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .username(testUsername)
                .userId("test-user-id")
                .build();
        UserContext.setUser(userInfo);

        // Mock 远程服务调用，返回空列表（表示分组中没有短链接）
        when(shortLinkRemoteService.listGroupShortLinkCount(anyList()))
                .thenReturn(Results.success(new ArrayList<>()));
    }

    @AfterEach
    void tearDown() {
        // 清理用户上下文
        UserContext.removeUser();
    }

    @Test
    void testSaveGroup() {
        // 测试创建分组
        String groupName = "测试分组_" + System.currentTimeMillis();

        assertDoesNotThrow(() -> groupService.saveGroup(testUsername, groupName), "创建分组应该成功");

        // 验证分组已创建
        List<ShortlinkGroupRespDTO> groups = groupService.listGroup();
        assertNotNull(groups, "分组列表不应为空");
        assertTrue(groups.size() > 0, "应该至少有一个分组");

        boolean found = groups.stream()
                .anyMatch(g -> groupName.equals(g.getName()));
        assertTrue(found, "应该能找到刚创建的分组");
    }

    @Test
    void testListGroup() {
        // 创建多个分组
        String groupName1 = "分组1_" + System.currentTimeMillis();
        String groupName2 = "分组2_" + System.currentTimeMillis();

        groupService.saveGroup(testUsername, groupName1);
        groupService.saveGroup(testUsername, groupName2);

        // 查询分组列表
        List<ShortlinkGroupRespDTO> groups = groupService.listGroup();
        assertNotNull(groups, "分组列表不应为空");
        assertTrue(groups.size() >= 2, "应该至少有两个分组");
    }

    @Test
    void testUpdateGroup() {
        // 先创建一个分组
        String originalName = "原始分组名_" + System.currentTimeMillis();
        groupService.saveGroup(testUsername, originalName);

        // 获取分组 gid
        List<ShortlinkGroupRespDTO> groups = groupService.listGroup();
        ShortlinkGroupRespDTO group = groups.stream()
                .filter(g -> originalName.equals(g.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(group, "应该能找到刚创建的分组");

        // 更新分组名称
        String newName = "更新后分组名_" + System.currentTimeMillis();
        ShortlinkGroupUpdateReqDTO updateReq = new ShortlinkGroupUpdateReqDTO();
        updateReq.setGid(group.getGid());
        updateReq.setName(newName);

        assertDoesNotThrow(() -> groupService.updateGroup(updateReq), "更新分组应该成功");

        // 验证更新结果
        List<ShortlinkGroupRespDTO> updatedGroups = groupService.listGroup();
        boolean found = updatedGroups.stream()
                .anyMatch(g -> newName.equals(g.getName()) && group.getGid().equals(g.getGid()));
        assertTrue(found, "应该能找到更新后的分组");
    }

    @Test
    void testDeleteGroup() {
        // 先创建一个分组
        String groupName = "待删除分组_" + System.currentTimeMillis();
        groupService.saveGroup(testUsername, groupName);

        // 获取分组 gid
        List<ShortlinkGroupRespDTO> groups = groupService.listGroup();
        ShortlinkGroupRespDTO group = groups.stream()
                .filter(g -> groupName.equals(g.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(group, "应该能找到刚创建的分组");

        int beforeCount = groups.size();

        // 删除分组
        assertDoesNotThrow(() -> groupService.deleteGroup(group.getGid()), "删除分组应该成功");

        // 验证删除结果
        List<ShortlinkGroupRespDTO> afterGroups = groupService.listGroup();
        int afterCount = afterGroups.size();
        assertEquals(beforeCount - 1, afterCount, "分组数量应该减少1");

        boolean found = afterGroups.stream()
                .anyMatch(g -> group.getGid().equals(g.getGid()));
        assertFalse(found, "删除的分组不应该再存在");
    }

    @Test
    void testSortGroup() {
        // 创建多个分组
        String groupName1 = "排序分组1_" + System.currentTimeMillis();
        String groupName2 = "排序分组2_" + System.currentTimeMillis();

        groupService.saveGroup(testUsername, groupName1);
        groupService.saveGroup(testUsername, groupName2);

        // 获取分组列表
        List<ShortlinkGroupRespDTO> groups = groupService.listGroup();
        assertTrue(groups.size() >= 2, "应该至少有两个分组");

        // 构造排序请求（交换前两个分组的顺序）
        List<ShortlinkGroupSortReqDTO> sortReqs = new ArrayList<>();
        for (int i = 0; i < Math.min(2, groups.size()); i++) {
            ShortlinkGroupSortReqDTO sortReq = new ShortlinkGroupSortReqDTO();
            sortReq.setGid(groups.get(i).getGid());
            sortReq.setSortOrder(groups.size() - i); // 反转顺序
            sortReqs.add(sortReq);
        }

        // 执行排序
        assertDoesNotThrow(() -> groupService.sortGroup(sortReqs), "分组排序应该成功");
    }
}
