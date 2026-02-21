/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xhy.shortlink.biz.statsservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessLogsDO;
import com.xhy.shortlink.biz.statsservice.dao.entity.LinkAccessStatsDO;
import com.xhy.shortlink.biz.api.stats.dto.req.*;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短链接访问日志持久层
 */
public interface LinkAccessLogsMapper extends BaseMapper<LinkAccessLogsDO> {

    /**
     * 根据短链接查询 Top5 高频访问 IP
     */
    @Select("""
            SELECT tlal.ip, COUNT(tlal.ip) AS count
            FROM t_link tl
            INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
            WHERE tlal.full_short_url = #{param.fullShortUrl}
              AND tl.gid = #{param.gid}
              AND tl.del_flag = '0'
              AND tl.enable_status = #{param.enableStatus}
              AND tlal.create_time BETWEEN #{param.startDate} AND #{param.endDate}
            GROUP BY tlal.full_short_url, tl.gid, tlal.ip
            ORDER BY count DESC
            LIMIT 5
            """)
    List<HashMap<String, Object>> listTopIpByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组查询 Top5 高频访问 IP
     */
    @Select("""
            SELECT tlal.ip, COUNT(tlal.ip) AS count
            FROM t_link tl
            INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
            WHERE tl.gid = #{param.gid}
              AND tl.del_flag = '0'
              AND tl.enable_status = '0'
              AND tlal.create_time BETWEEN #{param.startDate} AND #{param.endDate}
            GROUP BY tl.gid, tlal.ip
            ORDER BY count DESC
            LIMIT 5
            """)
    List<HashMap<String, Object>> listTopIpByShortLinkGroup(@Param("param") ShortLinkStatsGroupReqDTO requestParam);

    /**
     * 根据短链接查询 PV/UV/UIP 汇总统计
     */
    @Select("""
            SELECT COUNT(tlal.user) AS pv, COUNT(DISTINCT tlal.user) AS uv, COUNT(DISTINCT tlal.ip) AS uip
            FROM t_link tl
            INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
            WHERE tlal.full_short_url = #{param.fullShortUrl}
              AND tl.gid = #{param.gid}
              AND tl.del_flag = '0'
              AND tl.enable_status = #{param.enableStatus}
              AND tlal.create_time BETWEEN #{param.startDate} AND #{param.endDate}
            GROUP BY tlal.full_short_url, tl.gid
            """)
    LinkAccessStatsDO findPvUvUidStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组查询 PV/UV/UIP 汇总统计
     */
    @Select("""
            SELECT COUNT(tlal.user) AS pv, COUNT(DISTINCT tlal.user) AS uv, COUNT(DISTINCT tlal.ip) AS uip
            FROM t_link tl
            INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
            WHERE tl.gid = #{param.gid}
              AND tl.del_flag = '0'
              AND tl.enable_status = '0'
              AND tlal.create_time BETWEEN #{param.startDate} AND #{param.endDate}
            GROUP BY tl.gid
            """)
    LinkAccessStatsDO findPvUvUidStatsByShortLinkGroup(@Param("param") ShortLinkStatsGroupReqDTO requestParam);

    /**
     * 根据短链接查询新老访客数量
     */
    @Select("""
            SELECT SUM(old_user) AS oldUserCnt, SUM(new_user) AS newUserCnt
            FROM (
                SELECT
                    CASE WHEN COUNT(DISTINCT DATE(tlal.create_time)) > 1 THEN 1 ELSE 0 END AS old_user,
                    CASE WHEN COUNT(DISTINCT DATE(tlal.create_time)) = 1
                         AND MAX(tlal.create_time) >= #{param.startDate}
                         AND MAX(tlal.create_time) <= #{param.endDate} THEN 1 ELSE 0 END AS new_user
                FROM t_link tl
                INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
                WHERE tlal.full_short_url = #{param.fullShortUrl}
                  AND tl.gid = #{param.gid}
                  AND tl.enable_status = #{param.enableStatus}
                  AND tl.del_flag = '0'
                GROUP BY tlal.user
            ) AS user_counts
            """)
    HashMap<String, Object> findUvTypeCntByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组查询新老访客数量
     */
    @Select("""
            SELECT SUM(old_user) AS oldUserCnt, SUM(new_user) AS newUserCnt
            FROM (
                SELECT
                    CASE WHEN COUNT(DISTINCT DATE(tlal.create_time)) > 1 THEN 1 ELSE 0 END AS old_user,
                    CASE WHEN COUNT(DISTINCT DATE(tlal.create_time)) = 1
                         AND MAX(tlal.create_time) >= #{param.startDate}
                         AND MAX(tlal.create_time) <= #{param.endDate} THEN 1 ELSE 0 END AS new_user
                FROM t_link tl
                INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
                WHERE tl.gid = #{param.gid}
                  AND tl.enable_status = '0'
                  AND tl.del_flag = '0'
                GROUP BY tlal.user
            ) AS user_counts
            """)
    HashMap<String, Object> findUvTypeCntByShortLinkGroup(@Param("param") ShortLinkStatsGroupReqDTO requestParam);

    /**
     * 根据用户列表查询单条短链接的访客类型（新/老访客）
     */
    @Select("<script> " +
            "SELECT tlal.user, " +
            "CASE WHEN MIN(tlal.create_time) BETWEEN #{param.startDate} AND #{param.endDate} THEN '新访客' ELSE '老访客' END AS uvType " +
            "FROM t_link tl " +
            "INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url " +
            "WHERE tlal.full_short_url = #{param.fullShortUrl} " +
            "AND tl.gid = #{param.gid} " +
            "AND tl.del_flag = '0' " +
            "AND tl.enable_status = #{param.enableStatus} " +
            "AND tlal.user IN " +
            "<foreach item='item' index='index' collection='param.userAccessLogsList' open='(' separator=',' close=')'> " +
            "#{item} " +
            "</foreach> " +
            "GROUP BY tlal.user" +
            "</script>")
    List<Map<String, Object>> selectUvTypeByUser(@Param("param") ShortLinkUvTypeReqDTO requestParam);

    /**
     * 根据用户列表查询分组下的访客类型（新/老访客）
     */
    @Select("<script> " +
            "SELECT tlal.user, " +
            "CASE WHEN MIN(tlal.create_time) BETWEEN #{param.startDate} AND #{param.endDate} THEN '新访客' ELSE '老访客' END AS uvType " +
            "FROM t_link tl " +
            "INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url " +
            "WHERE tl.gid = #{param.gid} " +
            "AND tl.del_flag = '0' " +
            "AND tl.enable_status = #{param.enableStatus} " +
            "AND tlal.user IN " +
            "<foreach item='item' index='index' collection='param.userAccessLogsList' open='(' separator=',' close=')'> " +
            "#{item} " +
            "</foreach> " +
            "GROUP BY tlal.user" +
            "</script>")
    List<Map<String, Object>> selectUvTypeByUserGruop(@Param("param") ShortLinkUvTypeReqDTO requestParam);

    /**
     * 根据分组分页查询访问日志
     */
    @Select("""
            SELECT tlal.*
            FROM t_link tl
            INNER JOIN t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
            WHERE tl.gid = #{param.gid}
              AND tl.del_flag = '0'
              AND tl.enable_status = '0'
              AND tlal.create_time BETWEEN #{param.startDate} AND #{param.endDate}
            ORDER BY tlal.create_time DESC
            """)
    IPage<LinkAccessLogsDO> selectGroupPage(@Param("param") ShortLinkStatsAccessRecordGroupReqDTO requestParam);
}
