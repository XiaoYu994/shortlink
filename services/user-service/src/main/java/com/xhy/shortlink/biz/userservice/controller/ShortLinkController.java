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
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkBaseInfoRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.userservice.toolkit.EasyExcelWebUtil;
import com.xhy.shortlink.biz.userservice.toolkit.ResultUtils;
import com.xhy.shortlink.framework.starter.convention.result.Result;
import com.xhy.shortlink.framework.starter.web.Results;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 短链接管理控制器（代理层，转发请求到 project-service）
 *
 * @author XiaoYu
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkRemoteService shortLinkRemoteService;

    /**
     * 创建短链接
     */
    @PostMapping("/api/short-link/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return shortLinkRemoteService.createShortLink(requestParam);
    }

    /**
     * 批量创建短链接并导出 Excel
     */
    @PostMapping("/api/short-link/admin/v1/create/batch")
    public void batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam, HttpServletResponse response) {
        ShortLinkBatchCreateRespDTO batchResult = ResultUtils.check(shortLinkRemoteService.batchCreateShortLink(requestParam));
        List<ShortLinkBaseInfoRespDTO> linkInfos = batchResult.getBaseLinkInfos();
        EasyExcelWebUtil.write(response, "批量创建短链接-SaaS短链接系统", ShortLinkBaseInfoRespDTO.class, linkInfos);
    }

    /**
     * 修改短链接
     */
    @PostMapping("/api/short-link/admin/v1/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        ResultUtils.check(shortLinkRemoteService.updateShortLink(requestParam));
        return Results.success();
    }

    /**
     * 分页查询短链接
     */
    @GetMapping("/api/short-link/admin/v1/page")
    public Result<Page<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return shortLinkRemoteService.pageShortLink(requestParam);
    }
}
