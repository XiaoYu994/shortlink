package com.xhy.shortlink.admin.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhy.shortlink.admin.dao.entity.GroupDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 分组持久层
 */
public interface GroupMapper extends BaseMapper<GroupDO> {

    /**
     * 自定义查询，忽略逻辑删除状态
     */
    @Select("SELECT * FROM t_group WHERE gid = #{gid}")
    GroupDO selectByGidIgnoreLogicDelete(@Param("gid") String gid);
}
