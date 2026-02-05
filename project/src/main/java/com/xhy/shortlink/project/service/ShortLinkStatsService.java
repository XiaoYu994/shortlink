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

package com.xhy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsAccessRecordGroupReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsGroupReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkStatsRespDTO;

/*
* 短链接监控服务层
* */
public interface ShortLinkStatsService {

    /**
     * 获取单个短链接监控数据
     *
     * @param requestParam 获取短链接监控数据入参
     * @return 短链接监控数据
     */
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);


    /**
     * 获取分组短链接监控数据
     *
     * @param requestParam 获取短链接监控数据分组入参
     * @return 短链接监控数据
     */
    ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkStatsGroupReqDTO requestParam);

    /**
     * 获取单个短链接日志监控数据
     *
     * @param requestParam 获取短链接监控日志数据入参
     * @return 短链接监控日志数据
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam);

    /**
     * 获取分组短链接日志监控数据
     *
     * @param requestParam 获取短链接监控日志数据入参
     * @return 短链接监控日志数据
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(ShortLinkStatsAccessRecordGroupReqDTO requestParam);
}
