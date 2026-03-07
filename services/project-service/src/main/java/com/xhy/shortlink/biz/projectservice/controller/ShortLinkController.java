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
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkCoreService;
import com.xhy.shortlink.biz.projectservice.service.UrlTitleService;
import com.xhy.shortlink.biz.projectservice.service.impl.ShortLinkRedirectServiceImpl;
import com.xhy.shortlink.framework.starter.convention.result.Result;
import com.xhy.shortlink.framework.starter.idempotent.annotation.Idempotent;
import com.xhy.shortlink.framework.starter.web.Results;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 短链接控制器
 *
 * @author XiaoYu
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkCoreService shortLinkCoreService;
    private final ShortLinkRedirectServiceImpl shortLinkRedirectService;
    private final UrlTitleService urlTitleService;

    /**
     * 短链接跳转
     */
    @GetMapping("/{short-uri}")
    public void redirect(@PathVariable("short-uri") String shortUri,
                         ServletRequest request, ServletResponse response) {
        shortLinkRedirectService.redirect(shortUri, request, response);
    }

    /**
     * 创建短链接
     */
    @Idempotent(message = "短链接正在创建中，请勿重复提交")
    @PostMapping("/api/short-link/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody @Valid ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortLinkCoreService.createShortLink(requestParam));
    }

    /**
     * 批量创建短链接
     */
    @PostMapping("/api/short-link/v1/create/batch")
    public Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(@RequestBody @Valid ShortLinkBatchCreateReqDTO requestParam) {
        return Results.success(shortLinkCoreService.batchCreateShortLink(requestParam));
    }

    /**
     * 修改短链接
     */
    @PutMapping("/api/short-link/v1/update")
    public Result<Void> updateShortLink(@RequestBody @Valid ShortLinkUpdateReqDTO requestParam) {
        shortLinkCoreService.updateShortLink(requestParam);
        return Results.success();
    }

    /**
     * 分页查询短链接
     */
    @GetMapping("/api/short-link/v1/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return Results.success(shortLinkCoreService.pageShortLink(requestParam));
    }

    /**
     * 查询分组下短链接数量
     */
    @GetMapping("/api/short-link/v1/count")
    public Result<List<ShortLinkGroupCountRespDTO>> listGroupShortLinkCount(@RequestParam("gidList") List<String> requestParam) {
        return Results.success(shortLinkCoreService.listGroupShortLinkCount(requestParam));
    }

    /**
     * 获取网页标题
     */
    @GetMapping("/api/short-link/v1/title")
    public Result<String> getUrlTitle(@RequestParam("url") String url) {
        return Results.success(urlTitleService.getPageTitle(url));
    }
}
