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

package com.xhy.shortlink.biz.userservice.remote;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.biz.userservice.config.OpenFeignConfiguration;
import com.xhy.shortlink.biz.api.project.dto.req.*;
import com.xhy.shortlink.biz.api.project.dto.resp.*;
import com.xhy.shortlink.framework.starter.convention.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 短链接项目服务 Feign 远程调用客户端
 *
 * @author XiaoYu
 */
@FeignClient(
        value = "shortlink-project-service",
        configuration = OpenFeignConfiguration.class
)
public interface ShortLinkRemoteService {

    /**
     * 创建短链接
     */
    @PostMapping("/api/short-link/v1/create")
    Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam);

    /**
     * 批量创建短链接
     */
    @PostMapping("/api/short-link/v1/create/batch")
    Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam);

    /**
     * 修改短链接
     */
    @PostMapping("/api/short-link/v1/update")
    Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam);

    /**
     * 分页查询短链接
     */
    @GetMapping("/api/short-link/v1/page")
    Result<Page<ShortLinkPageRespDTO>> pageShortLink(@SpringQueryMap ShortLinkPageReqDTO requestParam);

    /**
     * 查询分组下短链接数量
     */
    @GetMapping("/api/short-link/v1/count")
    Result<List<ShortLinkGroupCountRespDTO>> listGroupShortLinkCount(@RequestParam("gidList") List<String> gidList);

    /**
     * 获取网页标题
     */
    @GetMapping("/api/short-link/v1/title")
    Result<String> getPageTitle(@RequestParam("url") String url);

    /**
     * 移入回收站
     */
    @PostMapping("/api/short-link/v1/recycle-bin/save")
    Result<Void> recycleBinSave(@RequestBody ShortLinkRecycleBinSaveReqDTO requestParam);

    /**
     * 分页查询回收站短链接
     */
    @GetMapping("/api/short-link/v1/recycle-bin/page")
    Result<Page<ShortLinkPageRespDTO>> pageRecycleShortLink(@SpringQueryMap ShortLinkRecycleBinPageReqDTO requestParam);

    /**
     * 恢复回收站短链接
     */
    @PostMapping("/api/short-link/v1/recycle-bin/recover")
    Result<Void> recoverShortLink(@RequestBody ShortLinkRecycleBinRecoverReqDTO requestParam);

    /**
     * 彻底删除回收站短链接
     */
    @PostMapping("/api/short-link/v1/recycle-bin/remove")
    Result<Void> removeShortLink(@RequestBody ShortLinkRecycleBinRemoveReqDTO requestParam);
}
