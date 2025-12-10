package com.xhy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;

import java.util.List;

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
     * 修改短链接
     * @param requestParam 请求参数
     * */
    void updateShortlink(ShortLinkUpdateReqDTO requestParam);


    /**
     * 分页查询短连接
     * @param requestParam 请求参数
     * @return 分页结果
     * */
    IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkPageReqDTO requestParam);

    /**
     * 查询短连接分组数量
     * @param requestParam 请求参数
     * @return 查询结果
     * */
    List<ShortLinkGroupCountRespDTO> listGroupShortlinkCount(List<String> requestParam);
}
