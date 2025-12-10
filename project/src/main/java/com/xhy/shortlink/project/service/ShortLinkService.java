package com.xhy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;

/*
* 短链接服务
 */
public interface ShortLinkService extends IService<ShortLinkDO> {

    /**
    * 创建短链接
    * @param requestParam 请求参数
     * @return 创建结果
    * */
    ShortLinkCreateRespDTO createShortlink(ShortLinkCreateReqDTO requestParam);

    /**
     * 分页查询短连接
     * @param requestParam 请求参数
     * @return 分页结果
     * */
    IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkPageReqDTO requestParam);
}
