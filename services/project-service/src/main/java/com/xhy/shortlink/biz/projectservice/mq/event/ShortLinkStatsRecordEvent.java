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

package com.xhy.shortlink.biz.projectservice.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接统计数据记录事件
 *
 * @author XiaoYu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsRecordEvent {

    /** 完整短链接（域名 + 短码），如 nurl.ink:8001/a1B2c3 */
    private String fullShortUrl;

    /** 访问者真实 IP 地址 */
    private String remoteAddr;

    /** 操作系统，如 Windows、Android、iOS */
    private String os;

    /** 浏览器类型，如 Chrome、Safari、Firefox */
    private String browser;

    /** 设备类型：Mobile / PC */
    private String device;

    /** 网络类型：WIFI / Mobile */
    private String network;

    /** UV 标识（Cookie 中的唯一值），用于独立访客去重 */
    private String uv;

    /** 短链接所属分组标识 */
    private String gid;

    /** 访问时间 */
    private Date currentDate;

    /** 事件唯一 ID，用于消息幂等去重 */
    private String eventId;
}
