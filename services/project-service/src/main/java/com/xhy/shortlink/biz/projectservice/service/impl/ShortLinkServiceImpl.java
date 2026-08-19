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

package com.xhy.shortlink.biz.projectservice.service.impl;

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
import com.xhy.shortlink.biz.projectservice.service.ShortLinkService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 短链接门面实现：委托给 Core CRUD 与 Redirect
 *
 * @author XiaoYu
 */
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkCoreService shortLinkCoreService;
    private final ShortLinkRedirectServiceImpl shortLinkRedirectService;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        return shortLinkCoreService.createShortLink(requestParam);
    }

    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        return shortLinkCoreService.batchCreateShortLink(requestParam);
    }

    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        shortLinkCoreService.updateShortLink(requestParam);
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return shortLinkCoreService.pageShortLink(requestParam);
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        return shortLinkCoreService.listGroupShortLinkCount(requestParam);
    }

    @Override
    public void fillTodayStats(ShortLinkPageRespDTO requestParam) {
        shortLinkCoreService.fillTodayStats(requestParam);
    }

    @Override
    public void redirect(String shortUri, ServletRequest request, ServletResponse response) {
        shortLinkRedirectService.redirect(shortUri, request, response);
    }
}
