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

package com.xhy.shortlink.biz.projectservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xhy.shortlink.framework.starter.database.base.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 历史短链接实体（归档后的过期/回收链接）
 *
 * @author XiaoYu
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_link_history")
public class ShortLinkHistoryDO extends BaseDO {

    /** 主键 ID */
    private Long id;
    /** 域名 */
    private String domain;
    /** 短链接后缀 */
    private String shortUri;
    /** 完整短链接 */
    private String fullShortUrl;
    /** 原始链接 */
    private String originUrl;
    /** 分组标识 */
    private String gid;
    /** 网站图标 */
    private String favicon;
    /** 启用状态 */
    private Integer enableStatus;
    /** 创建类型 */
    private Integer createdType;
    /** 有效期类型 */
    private Integer validDateType;

    /** 有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;

    /** 描述 */
    private String description;
    /** 历史总 PV */
    private Long totalPv;
    /** 历史总 UV */
    private Integer totalUv;
    /** 历史总 UIP */
    private Integer totalUip;
    /** 最后访问时间 */
    private Date lastAccessTime;
}
