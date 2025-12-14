package com.xhy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhy.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsGroupReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/*
* 短链接访问统计持久层
* */
public interface LinkAccessStatsMapper extends BaseMapper<LinkAccessStatsDO> {

    /*
    * 记录基础访问统计
    * */
    // 注解方式改为使用 <script> 标签包裹
    @Insert("<script>" +
            "INSERT INTO t_link_access_stats " +
            "(full_short_url, gid,date, pv, uv, uip, hour, weekday, create_time, update_time, del_flag) " +
            "VALUES " +
            "<foreach collection='list' item='item' separator=','> " +
            "(#{item.fullShortUrl},#{item.gid}, #{item.date}, #{item.pv}, #{item.uv}, #{item.uip}, " +
            "#{item.hour}, #{item.weekday}, NOW(), NOW(), 0) " +
            "</foreach> " +
            "ON DUPLICATE KEY UPDATE " +
            "pv = pv + VALUES(pv), " + // 注意这里使用了 VALUES() 函数引用新插入的值
            "uv = uv + VALUES(uv), " +
            "uip = uip + VALUES(uip), " +
            "update_time = NOW()" +  // 自动填充这里不会生效
            "</script>")
    void shortLinkStats(@Param("list") List<LinkAccessStatsDO> list);


    /*
    * 获取单个短链接基本数据统计
    * */
    @Select("select" +
            "    date,sum(pv) as pv,sum(uv) as uv,sum(uip) as uip " +
            "from" +
            "    t_link_access_stats " +
            "where" +
            "    full_short_url = #{param.fullShortUrl} and gid = #{param.gid} and date between #{param.startDate} and #{param.endDate}" +
            " group by" +
            "    full_short_url,gid,date;")
    List<LinkAccessStatsDO>  listStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /*
     * 获取分组短链接基本数据统计
     * */
    @Select("select" +
            "    date,sum(pv) as pv,sum(uv) as uv,sum(uip) as uip " +
            "from" +
            "    t_link_access_stats " +
            "where" +
            "    gid = #{param.gid} and date between #{param.startDate} and #{param.endDate}" +
            " group by" +
            "    gid,date;")
    List<LinkAccessStatsDO> listStatsByShortLinkGroup(@Param("param") ShortLinkStatsGroupReqDTO requestParam);


    /*
    * 根据短链接获取指定日期内小时基础监控数据
    * */
    @Select("select " +
            "   hour,sum(pv) as pv " +
            "from " +
            "   t_link_access_stats " +
            "where " +
            "   full_short_url = #{param.fullShortUrl} and gid = #{param.gid} and date between #{param.startDate} and #{param.endDate}" +
            "group by" +
            "    full_short_url,gid,hour;")
    List<LinkAccessStatsDO> listHourStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /*
     * 根据短链接获取指定日期内小时基础监控数据
     * */
    @Select("select " +
            "   hour,sum(pv) as pv " +
            "from " +
            "   t_link_access_stats " +
            "where " +
            "   gid = #{param.gid} and date between #{param.startDate} and #{param.endDate}" +
            "group by" +
            "   gid,hour;")
    List<LinkAccessStatsDO> listHourStatsByShortLinkGroup(@Param("param") ShortLinkStatsGroupReqDTO requestParam);


    /*
     * 根据短链接获取指定日期内周基础监控数据
     */
    @Select("SELECT " +
            "    tlas.weekday, " +
            "    SUM(tlas.pv) AS pv " +
            "FROM " +
            "    t_link tl INNER JOIN " +
            "    t_link_access_stats tlas ON tl.full_short_url = tlas.full_short_url " +
            "WHERE " +
            "    tlas.full_short_url = #{param.fullShortUrl} " +
            "    AND tl.gid = #{param.gid} " +
            "    AND tl.del_flag = '0' " +
            "    AND tl.enable_status = #{param.enableStatus} " +
            "    AND tlas.date BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    tlas.full_short_url, tl.gid, tlas.weekday;")
    List<LinkAccessStatsDO> listWeekdayStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);


    /*
     * 根据分组获取指定日期内周基础监控数据
     */
    @Select("SELECT " +
            "    tlas.weekday, " +
            "    SUM(tlas.pv) AS pv " +
            "FROM " +
            "    t_link tl INNER JOIN " +
            "    t_link_access_stats tlas ON tl.full_short_url = tlas.full_short_url " +
            "WHERE " +
            "    tl.gid = #{param.gid} " +
            "    AND tl.del_flag = '0' " +
            "    AND tl.enable_status = '0' " +
            "    AND tlas.date BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    tl.gid, tlas.weekday;")
    List<LinkAccessStatsDO> listWeekdayStatsByShortLinkGroup(@Param("param") ShortLinkStatsGroupReqDTO requestParam);
}
