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

import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.biz.projectservice.dto.resp.ShortLinkCreateRespDTO;

/**
 * 短链接创建服务接口
 *
 * @author XiaoYu
 */
public interface ShortLinkService {

    /**
     * 创建短链接
     *
     * @param requestParam 创建请求参数
     * @return 创建结果
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    /**
     * 批量创建短链接
     *
     * @param requestParam 批量创建请求参数
     * @return 批量创建结果
     */
    ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam);
}
