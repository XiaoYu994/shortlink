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

package com.xhy.shortlink.biz.projectservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkPageRespDTO;

import java.util.List;

/**
 * 短链接核心 CRUD 服务接口
 *
 * @author XiaoYu
 */
public interface ShortLinkCoreService {

    /**
     * 创建短链接
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    /**
     * 批量创建短链接
     */
    ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam);

    /**
     * 修改短链接
     */
    void updateShortLink(ShortLinkUpdateReqDTO requestParam);

    /**
     * 分页查询短链接
     */
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

    /**
     * 查询分组下短链接数量
     */
    List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam);

    /**
     * 从 Redis ZSet 中获取今日实时统计数据
     */
    void fillTodayStats(ShortLinkPageRespDTO requestParam);
}
