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

package com.xhy.shortlink.biz.statsservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.biz.statsservice.dto.req.ShortLinkStatsAccessRecordGroupReqDTO;
import com.xhy.shortlink.biz.statsservice.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.xhy.shortlink.biz.statsservice.dto.req.ShortLinkStatsGroupReqDTO;
import com.xhy.shortlink.biz.statsservice.dto.req.ShortLinkStatsReqDTO;
import com.xhy.shortlink.biz.statsservice.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.xhy.shortlink.biz.statsservice.dto.resp.ShortLinkStatsRespDTO;
import com.xhy.shortlink.biz.statsservice.service.ShortLinkStatsService;
import com.xhy.shortlink.framework.starter.convention.result.Result;
import com.xhy.shortlink.framework.starter.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接统计查询接口
 *
 * @author XiaoYu
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkStatsController {

    private final ShortLinkStatsService shortLinkStatsService;

    /**
     * 单个短链接监控统计
     */
    @GetMapping("/api/short-link/v1/stats")
    public Result<ShortLinkStatsRespDTO> shortLinkStats(ShortLinkStatsReqDTO requestParam) {
        return Results.success(shortLinkStatsService.oneShortLinkStats(requestParam));
    }

    /**
     * 分组短链接监控统计
     */
    @GetMapping("/api/short-link/v1/stats/group")
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(ShortLinkStatsGroupReqDTO requestParam) {
        return Results.success(shortLinkStatsService.groupShortLinkStats(requestParam));
    }

    /**
     * 单个短链接访问记录
     */
    @GetMapping("/api/short-link/v1/stats/access-record")
    public Result<IPage<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam) {
        return Results.success(shortLinkStatsService.shortLinkStatsAccessRecord(requestParam));
    }

    /**
     * 分组短链接访问记录
     */
    @GetMapping("/api/short-link/v1/stats/access-record/group")
    public Result<IPage<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(ShortLinkStatsAccessRecordGroupReqDTO requestParam) {
        return Results.success(shortLinkStatsService.groupShortLinkStatsAccessRecord(requestParam));
    }
}
