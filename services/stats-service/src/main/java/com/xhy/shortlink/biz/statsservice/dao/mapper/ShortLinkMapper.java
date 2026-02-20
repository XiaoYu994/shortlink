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
import com.xhy.shortlink.biz.statsservice.dao.entity.ShortLinkDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 短链接持久层（stats-service 精简版，仅用于 incrementStats）
 */
public interface ShortLinkMapper extends BaseMapper<ShortLinkDO> {

    @Update("UPDATE t_link "
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
