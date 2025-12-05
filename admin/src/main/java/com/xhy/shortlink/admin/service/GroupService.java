package com.xhy.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.admin.dao.entity.GroupDO;
import com.xhy.shortlink.admin.dto.req.ShortlinKGroupAddRespDTO;

/*
 * 分组接口层
 */
public interface GroupService extends IService<GroupDO> {
   /**
   * 新增短链接分组
   * @param requestParam 新增分组参数
   * */
    void addGroup(ShortlinKGroupAddRespDTO requestParam);
}
