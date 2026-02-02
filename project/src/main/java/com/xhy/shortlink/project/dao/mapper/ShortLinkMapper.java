package com.xhy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/*
* 短链接持久层
* */
public interface ShortLinkMapper extends BaseMapper<ShortLinkDO> {
    /**
     * 自定义聚合查询
     * ${ew.customSqlSegment} 会自动生成：WHERE ... GROUP BY ...
     */
    @Select("SELECT gid, count(*) as shortLinkCount FROM t_link ${ew.customSqlSegment}")
    List<ShortLinkGroupCountRespDTO> selectGroupCount(@Param(Constants.WRAPPER) Wrapper<ShortLinkDO> wrapper);

    /**
     * 专门用于修改分组时的“先删后增”操作 物理删除
     */
    @Delete("DELETE FROM t_link WHERE gid = #{gid} AND full_short_url = #{fullShortUrl}")
    void deletePhysical(@Param("gid") String gid, @Param("fullShortUrl") String fullShortUrl);

    // 统计降级数据数量
    @Select("""
        SELECT COUNT(*)
        FROM t_link
        WHERE gid = #{gid}
          AND del_flag = 0
          AND enable_status = 0
          AND (last_access_time < #{todayStart} OR last_access_time IS NULL)
""")
    long countLinkFallback(@Param("gid") String gid,@Param("todayStart") Date todayStart);

    /*
    * 短链接访问统计数据自增，同时更新最后访问时间
    * */
    @Update("UPDATE t_link " +
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


    /*
     * 分页统计短链接
     */
    IPage<ShortLinkDO> pageLink(ShortLinkPageReqDTO shortLinkPageReqDTO);

    /*
    * 分页统计回收站链接
    * */
    IPage<ShortLinkDO> pageRecycleBinLink(ShortLinkRecycleBinPageReqDTO requestParam);

    // 查询降级数据（无流量数据）
    List<ShortLinkDO> pageLinkFallback(@Param("gid") String gid,
                                       @Param("todayStart") Date todayStart,
                                       @Param("start") long start,
                                       @Param("size") long size);


}
