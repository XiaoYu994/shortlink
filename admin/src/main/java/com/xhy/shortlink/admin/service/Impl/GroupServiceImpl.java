package com.xhy.shortlink.admin.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.admin.common.biz.user.UserContext;
import com.xhy.shortlink.admin.common.convention.exception.ClientException;
import com.xhy.shortlink.admin.dao.entity.GroupDO;
import com.xhy.shortlink.admin.dao.mapper.GroupMapper;
import com.xhy.shortlink.admin.dto.req.ShortlinKGroupAddRespDTO;
import com.xhy.shortlink.admin.service.GroupService;
import com.xhy.shortlink.admin.toolkit.RandomCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {
    @Override
    public void addGroup(ShortlinKGroupAddRespDTO requestParam) {
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
            throw new ClientException("添加分组失败");
        }
    }
}
