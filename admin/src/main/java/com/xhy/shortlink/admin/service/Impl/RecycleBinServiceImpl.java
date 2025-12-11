package com.xhy.shortlink.admin.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.admin.common.biz.user.UserContext;
import com.xhy.shortlink.admin.common.convention.exception.ClientException;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.dao.entity.GroupDO;
import com.xhy.shortlink.admin.dao.mapper.GroupMapper;
import com.xhy.shortlink.admin.remote.ShortLinkRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkRecycleBinPageRespDTO;
import com.xhy.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/*
* 回收站接口实现层
* */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    /*
     * TODO 后续重构为SpringCloud Feign调用
     * */
    ShortLinkRemoteService shortlinkRemoteService = new ShortLinkRemoteService(){
    };
    private final GroupMapper groupMapper;

    @Override
    public Result<IPage<ShortLinkRecycleBinPageRespDTO>> pageRecycleShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        final LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class).eq(GroupDO::getUsername, UserContext.getUsername());
        final List<GroupDO> groupDOList = groupMapper.selectList(queryWrapper);
        if(groupDOList.isEmpty()) {
            throw new ClientException("用户没有分组信息");
        }
        requestParam.setGidList(groupDOList.stream().map(GroupDO::getGid).toList());
        return shortlinkRemoteService.pageRecycleShortlink(requestParam);
    }
}
