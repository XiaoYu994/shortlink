package com.xhy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.project.dao.entity.ShortlinkDO;
import com.xhy.shortlink.project.dto.req.ShortlinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortlinkPageReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortlinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortlinkPageRespDTO;

/*
* 短链接服务
 */
public interface ShortlinkService extends IService<ShortlinkDO> {

    /**
    * 创建短链接
    * @param requestParam 请求参数
     * @return 创建结果
    * */
    ShortlinkCreateRespDTO createShortlink(ShortlinkCreateReqDTO requestParam);

    /**
     * 分页查询短连接
     * @param requestParam 请求参数
     * @return 分页结果
     * */
    IPage<ShortlinkPageRespDTO> pageShortlink(ShortlinkPageReqDTO requestParam);
}
