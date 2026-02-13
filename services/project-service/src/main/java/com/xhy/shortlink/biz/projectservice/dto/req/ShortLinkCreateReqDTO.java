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

package com.xhy.shortlink.biz.projectservice.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 创建短链接请求参数
 *
 * @author XiaoYu
 */
@Data
public class ShortLinkCreateReqDTO {

    /** 域名 */
    private String domain;

    /** 原始链接 */
    @NotBlank(message = "原始链接不能为空")
    private String originUrl;

    /** 分组标识 */
    @NotBlank(message = "分组标识不能为空")
    private String gid;

    /** 创建类型 0：控制台 1：接口 */
    @NotNull(message = "创建类型不能为空")
    private Integer createdType;

    /** 有效期类型 0：永久有效 1：自定义 */
    @NotNull(message = "有效期类型不能为空")
    private Integer validDateType;

    /** 有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;

    /** 短链接描述 */
    private String description;
}
