package com.xhy.shortlink.admin.service.Impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/*
* 回收站接口实现层
* */
@Service(value = "recycleBinServiceImplByAdmin")
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    @Override
    public Result<Page<ShortLinkPageRespDTO>> pageRecycleShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return shortLinkActualRemoteService.pageRecycleShortlink(requestParam);
    }
}
