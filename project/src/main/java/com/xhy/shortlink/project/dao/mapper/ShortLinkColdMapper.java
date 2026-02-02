package com.xhy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.xhy.shortlink.project.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ShortLinkColdMapper extends BaseMapper<ShortLinkColdDO> {

    /**
     * 冷库分组数量统计
     */
    @Select("SELECT gid, count(*) as shortLinkCount FROM t_link_cold ${ew.customSqlSegment}")
    List<ShortLinkGroupCountRespDTO> selectGroupCount(@Param(Constants.WRAPPER) Wrapper<ShortLinkColdDO> wrapper);

    @Update("UPDATE t_link_cold " +
            "SET total_pv = total_pv + #{pv}, " +
            "    total_uv = total_uv + #{uv}, " +
            "    total_uip = total_uip + #{uip}, " +
            "    last_access_time = NOW() " +
            "WHERE gid = #{gid} " +
            "  AND full_short_url = #{fullShortUrl}")
    int incrementStats(@Param("gid") String gid,
                       @Param("fullShortUrl") String fullShortUrl,
                       @Param("pv") int pv,
                       @Param("uv") int uv,
                       @Param("uip") int uip);
}
