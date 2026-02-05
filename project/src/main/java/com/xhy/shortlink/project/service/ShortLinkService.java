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
import com.xhy.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.util.List;

/*
* 短链接服务
 */
public interface ShortLinkService extends IService<ShortLinkDO> {

    /**
     * 跳转短链接
     * @param shortUri 短链接
     * */
    void redirect(String shortUri,ServletRequest request, ServletResponse response);


    /**
    * 创建短链接 布隆过滤器
    * @param requestParam 请求参数
     * @return 创建结果
    * */
    ShortLinkCreateRespDTO createShortlink(ShortLinkCreateReqDTO requestParam);

    /**
     * 创建短链接 分布式锁
     * @param requestParam 请求参数
     * @return 创建结果
     * */
    ShortLinkCreateRespDTO createShortLinkByLock(ShortLinkCreateReqDTO requestParam);

    /**
     * 创建批量短链接
     * @param requestParam 请求参数
     * @return 创建结果
     * */
    ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam);

    /**
     * 修改短链接
     * @param requestParam 请求参数
     * */
    void updateShortlink(ShortLinkUpdateReqDTO requestParam);


    /**
     * 分页查询短连接
     * @param requestParam 请求参数
     * @return 分页结果
     * */
    IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkPageReqDTO requestParam);

    /**
     * 查询分组下短链接数量
     * @param requestParam 请求参数
     * @return 查询结果
     * */
    List<ShortLinkGroupCountRespDTO> listGroupShortlinkCount(List<String> requestParam);

    /**
     * 从 Redis Zset 中获取今日实时统计的数据
     * @param requestParam 请求参数
     */
    void fillTodayStats(ShortLinkPageRespDTO requestParam);

    /**
     * 冷库回热
     * @param fullShortUrl 完整短链接
     * @param gid 分组标识
     */
    void rehotColdLink(String fullShortUrl, String gid);

}
