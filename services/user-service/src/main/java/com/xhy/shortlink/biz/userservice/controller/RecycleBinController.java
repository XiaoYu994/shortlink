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

package com.xhy.shortlink.biz.userservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.biz.userservice.remote.ShortLinkRemoteService;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.userservice.service.RecycleBinService;
import com.xhy.shortlink.biz.userservice.toolkit.ResultUtils;
import com.xhy.shortlink.framework.starter.convention.result.Result;
import com.xhy.shortlink.framework.starter.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站管理控制器
 *
 * @author XiaoYu
 */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {

    private final ShortLinkRemoteService shortLinkRemoteService;
    private final RecycleBinService recycleBinService;

    /**
     * 移入回收站
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/save")
    public Result<Void> saveRecycleBin(@RequestBody ShortLinkRecycleBinSaveReqDTO requestParam) {
        ResultUtils.check(shortLinkRemoteService.recycleBinSave(requestParam));
        return Results.success();
    }

    /**
     * 分页查询回收站短链接
     */
    @GetMapping("/api/short-link/admin/v1/recycle-bin/page")
    public Result<Page<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO requestParam) {
        return recycleBinService.pageRecycleShortLink(requestParam);
    }

    /**
     * 恢复回收站短链接
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/recover")
    public Result<Void> recoverRecycleBin(@RequestBody ShortLinkRecycleBinRecoverReqDTO requestParam) {
        ResultUtils.check(shortLinkRemoteService.recoverShortLink(requestParam));
        return Results.success();
    }

    /**
     * 彻底删除回收站短链接
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/remove")
    public Result<Void> removeRecycleBin(@RequestBody ShortLinkRecycleBinRemoveReqDTO requestParam) {
        ResultUtils.check(shortLinkRemoteService.removeShortLink(requestParam));
        return Results.success();
    }
}
