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

import com.baomidou.mybatisplus.annotation.TableField;
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
 * 短链接主表实体
 *
 * @author XiaoYu
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_link")
public class ShortLinkDO extends BaseDO {

    /** ID */
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

    /** 启用标识 0：启用 1：未启用 2：平台封禁 3：冻结 */
    private Integer enableStatus;

    /** 创建类型 0：控制台 1：接口 */
    private Integer createdType;

    /** 有效期类型 0：永久有效 1：用户自定义 */
    private Integer validDateType;

    /** 有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;

    /** 描述 */
    private String description;

    /** 历史 PV */
    private Long totalPv;

    /** 历史 UV */
    private Integer totalUv;

    /** 历史 UIP */
    private Integer totalUip;

    /** 今日 PV（非数据库字段） */
    @TableField(exist = false)
    private Integer todayPv;

    /** 今日 UV（非数据库字段） */
    @TableField(exist = false)
    private Integer todayUv;

    /** 今日 UIP（非数据库字段） */
    @TableField(exist = false)
    private Integer todayUip;

    /** 最后访问时间 */
    private Date lastAccessTime;
}
