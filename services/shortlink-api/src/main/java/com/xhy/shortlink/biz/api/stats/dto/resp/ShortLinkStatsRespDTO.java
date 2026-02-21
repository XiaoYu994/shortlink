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

package com.xhy.shortlink.biz.api.stats.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 短链接统计汇总响应参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsRespDTO {

    /** 总 PV */
    private Integer pv;

    /** 总 UV */
    private Integer uv;

    /** 总 UIP */
    private Integer uip;

    /** 每日访问统计 */
    private List<ShortLinkStatsAccessDailyRespDTO> daily;

    /** 地区统计 */
    private List<ShortLinkStatsLocaleCNRespDTO> localeCnStats;

    /** 小时统计 */
    private List<Integer> hourStats;

    /** 高频 IP 统计 */
    private List<ShortLinkStatsTopIpRespDTO> topIpStats;

    /** 星期统计 */
    private List<Integer> weekdayStats;

    /** 浏览器统计 */
    private List<ShortLinkStatsBrowserRespDTO> browserStats;

    /** 操作系统统计 */
    private List<ShortLinkStatsOsRespDTO> osStats;

    /** UV 类型统计 */
    private List<ShortLinkStatsUvRespDTO> uvTypeStats;

    /** 设备统计 */
    private List<ShortLinkStatsDeviceRespDTO> deviceStats;

    /** 网络类型统计 */
    private List<ShortLinkStatsNetworkRespDTO> networkStats;
}
