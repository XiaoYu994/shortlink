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
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;

/**
 * 回收站服务接口
 */
public interface RecycleBinService {

    /** 移入回收站 */
    void recycleBinSave(ShortLinkRecycleBinSaveReqDTO requestParam);

    /** 分页查询回收站 */
    IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkRecycleBinPageReqDTO requestParam);

    /** 从回收站恢复 */
    void recoverShortlink(ShortLinkRecycleBinRecoverReqDTO requestParam);

    /** 从回收站永久删除 */
    void removeShortlink(ShortLinkRecycleBinRemoveReqDTO requestParam);
}
