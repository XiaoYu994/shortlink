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
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;

import java.util.List;

/**
 * 短链接核心 CRUD 服务接口
 *
 * @author XiaoYu
 */
public interface ShortLinkCoreService {

    /**
     * 创建短链接
     *
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建响应（包含完整短链接地址）
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    /**
     * 批量创建短链接
     *
     * @param requestParam 批量创建短链接请求参数
     * @return 批量创建响应（包含成功列表与总数）
     */
    ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam);

    /**
     * 修改短链接
     *
     * @param requestParam 修改短链接请求参数
     */
    void updateShortLink(ShortLinkUpdateReqDTO requestParam);

    /**
     * 分页查询短链接
     *
     * @param requestParam 分页查询请求参数
     * @return 短链接分页结果
     */
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

    /**
     * 查询分组下短链接数量
     *
     * @param requestParam 分组标识列表
     * @return 各分组对应的短链接数量
     */
    List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam);

    /**
     * 填充今日实时统计数据（从 Redis ZSet 获取）
     *
     * @param requestParam 需要填充统计数据的短链接分页记录
     */
    void fillTodayStats(ShortLinkPageRespDTO requestParam);
}
