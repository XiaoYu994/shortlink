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
    * 分组标识
    * */
    private List<String> gidList;
}
