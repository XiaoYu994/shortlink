package com.xhy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhy.shortlink.project.dao.entity.GroupDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分组持久层
 */
public interface GroupMapper extends BaseMapper<GroupDO> {


    /**
     *  查询当前用户下所有的 gid
     * @param username 用户名
     * @return gid 列表
     */
    @Select("select gid from t_group where username = #{username}")
    List<String> selectGidListByUsername(@Param("username") String username);
}
