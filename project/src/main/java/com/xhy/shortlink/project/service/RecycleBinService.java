package com.xhy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkRecycleBinPageRespDTO;

/*
* 回收站接口层
* */
public interface RecycleBinService extends IService<ShortLinkDO> {

    /**
     * 移动到回收站
     * @param requestParam 回收站请求参数
     */
    void recycleBinSave(ShortLinkRecycleBinSaveReqDTO requestParam);

    /**
     * 分页查询回收站链接
     * @param requestParam 请求参数
     * @return 分页结果
     */
    IPage<ShortLinkRecycleBinPageRespDTO> pageShortlink(ShortLinkRecycleBinPageReqDTO requestParam);

    /**
     * 恢复短链接
     * @param requestParam 请求参数
     */
    void recoverShortlink(ShortLinkRecycleBinRecoverReqDTO requestParam);

    /**
     * 移除短链接
     * @param requestParam 请求参数
     */
    void removeShortlink(ShortLinkRecycleBinRemoveReqDTO requestParam);
}
