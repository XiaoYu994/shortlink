package com.xhy.shortlink.admin.remote.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

/*
 * 分页查询回收站链接请求参数
 * */
@Data
public class ShortLinkRecycleBinPageReqDTO extends Page {
    /*
    * 分组标识
    * */
    private List<String> gidList;
}
