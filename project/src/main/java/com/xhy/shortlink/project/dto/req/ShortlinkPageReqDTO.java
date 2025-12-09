package com.xhy.shortlink.project.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.project.dao.entity.ShortlinkDO;
import lombok.Data;

/*
* 短链接分页请求参数
* */
@Data
public class ShortlinkPageReqDTO extends Page <ShortlinkDO> {

    /*
    * 分组id
    * */
    private String gid;
}
