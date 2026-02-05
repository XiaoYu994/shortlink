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

package com.xhy.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;

/*
* 回收站接口层
* */
public interface RecycleBinService extends IService<ShortLinkDO> {

    /**
     * 移动到回收站
     * @param requestParam 回收站请求参数
     */
    void recycleBinSave(ShortLinkRecycleBinSaveReqDTO requestParam);

    /**
     * 分页查询回收站链接
     * @param requestParam 请求参数
     * @return 分页结果
     */
    IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkRecycleBinPageReqDTO requestParam);

    /**
     * 恢复短链接
     * @param requestParam 请求参数
     */
    void recoverShortlink(ShortLinkRecycleBinRecoverReqDTO requestParam);

    /**
     * 移除短链接
     * @param requestParam 请求参数
     */
    void removeShortlink(ShortLinkRecycleBinRemoveReqDTO requestParam);
}
