package com.xhy.shortlink.admin.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.admin.common.biz.user.UserContext;
import com.xhy.shortlink.admin.common.convention.exception.ClientException;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.dao.entity.GroupDO;
import com.xhy.shortlink.admin.dao.mapper.GroupMapper;
import com.xhy.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkRecycleBinPageRespDTO;
import com.xhy.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/*
* 回收站接口实现层
* */
@Service(value = "recycleBinServiceImplByAdmin")
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final GroupMapper groupMapper;

    @Override
    public Result<Page<ShortLinkRecycleBinPageRespDTO>> pageRecycleShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        final LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class).eq(GroupDO::getUsername, UserContext.getUsername());
        final List<GroupDO> groupDOList = groupMapper.selectList(queryWrapper);
        if(groupDOList.isEmpty()) {
            throw new ClientException("用户没有分组信息");
        }
        requestParam.setGidList(groupDOList.stream().map(GroupDO::getGid).toList());
        return shortLinkActualRemoteService.pageRecycleShortlink(requestParam);
    }
}
