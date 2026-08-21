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

package com.xhy.shortlink.biz.statsservice.locale;

import com.xhy.shortlink.biz.statsservice.dao.entity.LinkLocaleStatsDO;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkAccessLogsMapper;
import com.xhy.shortlink.biz.statsservice.dao.mapper.LinkLocaleStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;

import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.LOCALE_COUNTRY_CN;

/**
 * 公网 IP 的高德解析与地区行补写。失败只影响省份，不影响 MQ 主流程入库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLocaleEnricher {

    private final IpLocationService ipLocationService;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;

    @Async("amapExecutor")
    public void enrich(String fullShortUrl, Date accessDate, String ip, Long accessLogId) {
        try {
            IpLocation location = ipLocationService.resolveRemote(ip);
            persistLocale(fullShortUrl, accessDate, location);
            if (accessLogId != null && !location.isUnknown()) {
                linkAccessLogsMapper.updateLocale(accessLogId, location.display());
            }
        } catch (Exception ex) {
            log.warn("异步补全地区失败, ip={}, url={}", ip, fullShortUrl, ex);
            persistLocale(fullShortUrl, accessDate, IpLocation.unknown());
        }
    }

    public void persistLocale(String fullShortUrl, Date accessDate, IpLocation location) {
        linkLocaleStatsMapper.shortLinkLocaleState(LinkLocaleStatsDO.builder()
                .fullShortUrl(fullShortUrl)
                .province(location.province())
                .city(location.city())
                .adcode(location.adcode())
                .cnt(1)
                .country(LOCALE_COUNTRY_CN)
                .date(accessDate)
                .build());
    }
}
