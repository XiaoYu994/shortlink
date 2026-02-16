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
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkCoreService;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 短链接服务 Facade 实现
 * <p>
 * 保留 {@code extends ServiceImpl} 以维持 {@code IService} 契约，
 * 所有业务逻辑委托给 {@link ShortLinkCoreService}。
 *
 * @author XiaoYu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final ShortLinkCoreService coreService;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        return coreService.createShortLink(requestParam);
    }

    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        return coreService.batchCreateShortLink(requestParam);
    }

    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        coreService.updateShortLink(requestParam);
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return coreService.pageShortLink(requestParam);
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        return coreService.listGroupShortLinkCount(requestParam);
    }

    @Override
    public void fillTodayStats(ShortLinkPageRespDTO requestParam) {
        coreService.fillTodayStats(requestParam);
    }
}
