package com.xhy.shortlink.project.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import lombok.Data;

import java.util.List;

/*
 * 分页查询回收站链接请求参数
 * */
@Data
public class ShortLinkRecycleBinPageReqDTO extends Page<ShortLinkDO> {
    /*
     * 排序标识
     * */
    private String orderTag;

    /*
    *  用户 gid 列表 主要用于后端查询后插入
    * */
    private List<String> gidList;
}
