package com.xhy.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.admin.dao.entity.GroupDO;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupAddReqDTO;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupSortReqDTO;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupUpdateReqDTO;
import com.xhy.shortlink.admin.dto.resp.ShortlinkGroupRespDTO;

import java.util.List;

/*
 * 分组接口层
 */
public interface GroupService extends IService<GroupDO> {
   /**
   * 新增短链接分组
   * @param requestParam 新增分组参数
   * */
    void addGroup(ShortlinkGroupAddReqDTO requestParam);

    /**
     * 查询短链接分组集合
     * @return 分组集合
     */
    List<ShortlinkGroupRespDTO> listGroup();

    /**
     * 修改短链接分组
     * @param requestParam 修改分组参数
     */
    void updateGroup(ShortlinkGroupUpdateReqDTO requestParam);

    /**
     * 删除短链接分组
     * @param gid 分组标识
     */
    void deleteGroup(String gid);

    /**
     * 分组排序
     * @param requestParam 排序参数
     */
    void sortGroup(List<ShortlinkGroupSortReqDTO> requestParam);
}
