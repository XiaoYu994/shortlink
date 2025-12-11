package com.xhy.shortlink.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinSaveReqDTO;

/*
* 回收站接口层
* */
public interface RecycleBinService extends IService<ShortLinkDO> {

    /**
     * 移动到回收站
     * @param requestParam 回收站请求参数
     */
    void recycleBinSave(ShortLinkRecycleBinSaveReqDTO requestParam);
}
