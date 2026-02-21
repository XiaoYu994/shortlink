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

package com.xhy.shortlink.biz.userservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.biz.userservice.dao.entity.GroupDO;
import com.xhy.shortlink.biz.userservice.dao.mapper.GroupMapper;
import com.xhy.shortlink.biz.userservice.dto.req.ShortlinkGroupSortReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.ShortlinkGroupUpdateReqDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.ShortlinkGroupRespDTO;
import com.xhy.shortlink.biz.userservice.remote.ShortLinkRemoteService;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.biz.userservice.service.GroupService;
import com.xhy.shortlink.biz.userservice.toolkit.ResultUtils;
import com.xhy.shortlink.framework.starter.common.enums.DelEnum;
import com.xhy.shortlink.framework.starter.common.toolkit.BeanUtil;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import com.xhy.shortlink.framework.starter.distributedid.toolkit.SnowflakeIdUtil;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.xhy.shortlink.biz.userservice.common.constant.RedisCacheConstant.LOCK_GROUP_CREATE_KEY;

/**
 * 短链分组接口实现层
 *
 */
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {
    private final RedissonClient redissonClient;
    private final ShortLinkRemoteService shortLinkRemoteService;

    @Value("${short-link.group.max-num}")
    private Integer groupMaxNum;
    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getUsername(), groupName);
    }

    @Override
    public void saveGroup(String username, String groupName) {
        final RLock lock = redissonClient.getLock(String.format(LOCK_GROUP_CREATE_KEY, username));
        lock.lock();
        try {
            final LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getUsername, username);
            final List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
            if (CollUtil.isNotEmpty(groupDOList) && groupDOList.size() >= groupMaxNum) {
                throw new ClientException(String.format("已超出最大分组数:%d", groupMaxNum));
            }
            // 雪花算法生成，数据库层面唯一索引兜底
            String gid = SnowflakeIdUtil.nextIdStr();
            final GroupDO groupDO = GroupDO.builder()
                    .gid(gid)
                    .name(groupName)
                    .username(username)
                    .build();
            baseMapper.insert(groupDO);
        } catch (DuplicateKeyException ex) {
            throw new ClientException("分组创建失败，请稍后重试");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ShortlinkGroupRespDTO> listGroup() {
        final LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, DelEnum.NORMAL.getCode())
                .orderByDesc(GroupDO::getSortOrder, GroupDO::getCreateTime);
        final List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
        if (CollUtil.isEmpty(groupDOList)) {
            return List.of();
        }
        List<ShortlinkGroupRespDTO> result = BeanUtil.convert(groupDOList, ShortlinkGroupRespDTO.class);
        List<String> gidList = result.stream().map(ShortlinkGroupRespDTO::getGid).toList();
        List<ShortLinkGroupCountRespDTO> countList = ResultUtils.check(shortLinkRemoteService.listGroupShortLinkCount(gidList));
        result.forEach(each -> countList.stream()
                .filter(count -> each.getGid().equals(count.getGid()))
                .findFirst()
                .ifPresent(count -> each.setShortLinkCount(count.getShortLinkCount())));
        return result;
    }

    @Override
    public void updateGroup(ShortlinkGroupUpdateReqDTO requestParam) {
        final LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, DelEnum.NORMAL.getCode())
                .eq(GroupDO::getGid, requestParam.getGid())
                .set(GroupDO::getName, requestParam.getName());
        baseMapper.update(null,updateWrapper);
    }

    @Override
    public void deleteGroup(String gid) {
        final LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, DelEnum.NORMAL.getCode())
                .eq(GroupDO::getGid, gid);
        final GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(DelEnum.DELETED.getCode());
        baseMapper.update(groupDO, updateWrapper);
    }

    @Override
    public void sortGroup(List<ShortlinkGroupSortReqDTO> requestParam) {
        requestParam.forEach(each -> {
            final GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())
                    .build();
            baseMapper.update(groupDO, Wrappers.lambdaUpdate(GroupDO.class)
            .eq(GroupDO::getUsername, UserContext.getUsername())
            .eq(GroupDO::getGid, each.getGid())
            .eq(GroupDO::getDelFlag, DelEnum.NORMAL.getCode()));
        });
    }
}
