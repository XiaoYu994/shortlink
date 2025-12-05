package com.xhy.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.admin.common.biz.user.UserContext;
import com.xhy.shortlink.admin.common.convention.exception.ClientException;
import com.xhy.shortlink.admin.dao.entity.GroupDO;
import com.xhy.shortlink.admin.dao.mapper.GroupMapper;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupAddReqDTO;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupSortReqDTO;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupUpdateReqDTO;
import com.xhy.shortlink.admin.dto.resp.ShortlinkGroupRespDTO;
import com.xhy.shortlink.admin.service.GroupService;
import com.xhy.shortlink.admin.toolkit.RandomCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.xhy.shortlink.admin.common.convention.errorcode.BaseErrorCode.SERVICE_SAVE_ERROR;
import static com.xhy.shortlink.admin.common.convention.errorcode.BaseErrorCode.SERVICE_UPDATE_ERROR;

@Slf4j
@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {
    @Override
    public void addGroup(ShortlinkGroupAddReqDTO requestParam) {
        //gid 使用随机生成的6位数
        String gid = RandomCodeGenerator.generateSixDigitCode();
        // 插入之前要查询gid是否已经存在
        GroupDO groupDO = baseMapper.selectOne(Wrappers.lambdaQuery(GroupDO.class).eq(GroupDO::getGid,gid));
        while (groupDO != null) {
            gid = RandomCodeGenerator.generateSixDigitCode();
            groupDO = baseMapper.selectOne(Wrappers.lambdaQuery(GroupDO.class).eq(GroupDO::getGid,gid));
        }
        final GroupDO group = GroupDO.builder()
                .gid(gid)
                .name(requestParam.getName())
                .username(UserContext.getUsername())
                .build();
        final int insert = baseMapper.insert(group);
        if (insert <= 0) {
            throw new ClientException(SERVICE_SAVE_ERROR);
        }
    }

    @Override
    public List<ShortlinkGroupRespDTO> listGroup() {
        // 1. 根据用户名进行查询
        final LambdaQueryWrapper<GroupDO>  queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .orderByDesc(GroupDO::getSortOrder, GroupDO::getCreateTime);
        final List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
        return BeanUtil.copyToList(groupDOList, ShortlinkGroupRespDTO.class);
    }

    @Override
    public void updateGroup(ShortlinkGroupUpdateReqDTO requestParam) {
        final LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, requestParam.getGid());
        final GroupDO groupDO = GroupDO.builder().name(requestParam.getName())
                .build();
        final int update = baseMapper.update(groupDO, updateWrapper);
        if (update <= 0) {
            throw new ClientException(SERVICE_UPDATE_ERROR);
        }
    }

    @Override
    public void deleteGroup(String gid) {
        final int delete = baseMapper.delete(Wrappers.lambdaQuery(GroupDO.class).eq(GroupDO::getGid, gid).
                eq(GroupDO::getUsername, UserContext.getUsername()));
        if (delete <= 0) {
            throw new ClientException(SERVICE_UPDATE_ERROR);
        }
    }

    @Override
    public void sortGroup(List<ShortlinkGroupSortReqDTO> requestParam) {
        requestParam.forEach(item -> {
            final GroupDO groupDO = GroupDO.builder()
                    .sortOrder(item.getSortOrder())
                    .build();
            final int update = baseMapper.update(groupDO, Wrappers.lambdaUpdate(GroupDO.class)
                    .eq(GroupDO::getGid, item.getGid())
                    .eq(GroupDO::getUsername, UserContext.getUsername()));
            if(update <= 0){
                throw new ClientException(SERVICE_UPDATE_ERROR);
            }
        });
    }
}
