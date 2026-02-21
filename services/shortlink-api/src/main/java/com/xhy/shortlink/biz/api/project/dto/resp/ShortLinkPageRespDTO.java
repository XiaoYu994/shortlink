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

package com.xhy.shortlink.biz.api.project.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 短链接分页查询响应参数
 */
@Data
public class ShortLinkPageRespDTO {

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

    /** 启用状态 0:启用 1:未启用 */
    private Integer enableStatus;

    /** 有效期类型 0:永久有效 1:自定义 */
    private Integer validDateType;

    /** 有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 描述 */
    private String description;

    /** 历史 PV */
    private Integer totalPv;

    /** 今日 PV */
    private Integer todayPv;

    /** 历史 UV */
    private Integer totalUv;

    /** 今日 UV */
    private Integer todayUv;

    /** 历史 UIP */
    private Integer totalUip;

    /** 今日 UIP */
    private Integer todayUip;
}
