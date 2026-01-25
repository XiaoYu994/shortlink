package com.xhy.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;

/*
*回收站接口层
* */
public interface RecycleBinService {

    /**
     * 分页查询回收站链接
     * @param requestParam 请求参数
     * @return 分页结果
     */
    Result<Page<ShortLinkPageRespDTO>> pageRecycleShortlink(ShortLinkRecycleBinPageReqDTO requestParam);
}
