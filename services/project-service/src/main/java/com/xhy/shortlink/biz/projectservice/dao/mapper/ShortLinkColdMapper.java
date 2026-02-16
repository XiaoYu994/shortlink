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

package com.xhy.shortlink.biz.projectservice.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkGroupCountRespDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * 冷库短链接持久层
 *
 * @author XiaoYu
 */
public interface ShortLinkColdMapper extends BaseMapper<ShortLinkColdDO> {

    /**
     * 冷库分组数量统计
     */
    @Select("SELECT gid, count(*) as shortLinkCount FROM t_link_cold ${ew.customSqlSegment}")
    List<ShortLinkGroupCountRespDTO> selectGroupCount(@Param(Constants.WRAPPER) Wrapper<ShortLinkColdDO> wrapper);

    /**
     * 统计无今日流量的冷库链接数量（降级场景）
     */
    @Select("""
        SELECT COUNT(*)
        FROM t_link_cold
        WHERE gid = #{gid}
          AND del_flag = 0
          AND enable_status IN (0, 3)
          AND (last_access_time < #{todayStart} OR last_access_time IS NULL)
""")
    long countLinkFallback(@Param("gid") String gid, @Param("todayStart") Date todayStart);

    /**
     * 冷库统计数据自增
     */
    @Update("UPDATE t_link_cold "
            + "SET total_pv = total_pv + #{pv}, "
            + "    total_uv = total_uv + #{uv}, "
            + "    total_uip = total_uip + #{uip}, "
            + "    last_access_time = NOW() "
            + "WHERE gid = #{gid} "
            + "  AND full_short_url = #{fullShortUrl}")
    int incrementStats(@Param("gid") String gid,
                       @Param("fullShortUrl") String fullShortUrl,
                       @Param("pv") int pv,
                       @Param("uv") int uv,
                       @Param("uip") int uip);
}
