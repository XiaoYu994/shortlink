package com.xhy.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhy.shortlink.project.dao.entity.LinkAccessStatsDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

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
}
