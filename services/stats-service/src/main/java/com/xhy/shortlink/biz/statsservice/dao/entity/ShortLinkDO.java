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

package com.xhy.shortlink.biz.statsservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xhy.shortlink.framework.starter.database.base.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接主表实体（stats-service 精简版，仅用于 incrementStats 和 JOIN 查询）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_link")
public class ShortLinkDO extends BaseDO {

    private Long id;
    private String domain;
    private String shortUri;
    private String fullShortUrl;
    private String originUrl;
    private String gid;
    private Integer enableStatus;
    private Long totalPv;
    private Integer totalUv;
    private Integer totalUip;
    private Date lastAccessTime;
}
