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

package com.xhy.shortlink.biz.projectservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.projectservice.service.RecycleBinService;
import com.xhy.shortlink.framework.starter.convention.result.Result;
import com.xhy.shortlink.framework.starter.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站控制器
 */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    /**
     * 移入回收站
     */
    @PostMapping("/api/short-link/v1/recycle-bin/save")
    public Result<Void> recycleBinSave(@RequestBody ShortLinkRecycleBinSaveReqDTO requestParam) {
        recycleBinService.recycleBinSave(requestParam);
        return Results.success();
    }

    /**
     * 分页查询回收站
     */
    @GetMapping("/api/short-link/v1/recycle-bin/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageRecycleBinShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return Results.success(recycleBinService.pageShortlink(requestParam));
    }

    /**
     * 从回收站恢复
     */
    @PostMapping("/api/short-link/v1/recycle-bin/recover")
    public Result<Void> recoverShortlink(@RequestBody ShortLinkRecycleBinRecoverReqDTO requestParam) {
        recycleBinService.recoverShortlink(requestParam);
        return Results.success();
    }

    /**
     * 从回收站永久删除
     */
    @PostMapping("/api/short-link/v1/recycle-bin/remove")
    public Result<Void> removeShortlink(@RequestBody ShortLinkRecycleBinRemoveReqDTO requestParam) {
        recycleBinService.removeShortlink(requestParam);
        return Results.success();
    }
}
