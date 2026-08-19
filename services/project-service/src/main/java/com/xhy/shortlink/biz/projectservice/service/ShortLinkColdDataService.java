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

import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;

import java.util.List;
import java.util.function.Consumer;

/**
 * 冷热数据管理：迁移、回温、合并查询
 *
 * @author XiaoYu
 */
public interface ShortLinkColdDataService {

    /**
     * 归并已按相同规则排序的热库、冷库列表
     *
     * @param hotList      热库记录（已排序）
     * @param coldList     冷库记录（已排序）
     * @param orderTag     排序字段
     * @param afterConvert 转换后回调（例如填充今日统计）
     * @return 归并后的分页 DTO 列表
     */
    List<ShortLinkPageRespDTO> mergeSorted(
            List<ShortLinkDO> hotList,
            List<ShortLinkColdDO> coldList,
            String orderTag,
            Consumer<ShortLinkPageRespDTO> afterConvert);

    /**
     * 冷链接访问计数，达到阈值后迁回热库
     *
     * @param fullShortUrl 完整短链接
     * @param gid          分组标识
     */
    void tryRehot(String fullShortUrl, String gid);

    /**
     * 扫描热表并将不活跃链接迁移到冷库
     *
     * @return 成功迁移条数
     */
    int migrateInactiveLinks();
}
