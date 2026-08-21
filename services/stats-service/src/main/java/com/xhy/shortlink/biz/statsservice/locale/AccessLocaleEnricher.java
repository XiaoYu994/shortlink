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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.Executor;

import static com.xhy.shortlink.biz.statsservice.common.constant.ShortLinkConstant.LOCALE_COUNTRY_CN;

/**
 * 公网 IP 的高德解析与地区行补写。
 * <p>
 * 只在主统计全部写库成功之后提交。不在 MQ {@code @Idempotent} 保护内：
 * 主流程失败则不会入队；入队后的失败/丢弃只写「未知」地区行，不回补 PV。
 */
@Slf4j
@Component
public class AccessLocaleEnricher {

    private final IpLocationService ipLocationService;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final Executor amapExecutor;

    public AccessLocaleEnricher(IpLocationService ipLocationService,
                                LinkLocaleStatsMapper linkLocaleStatsMapper,
                                LinkAccessLogsMapper linkAccessLogsMapper,
                                @Qualifier("amapExecutor") Executor amapExecutor) {
        this.ipLocationService = ipLocationService;
        this.linkLocaleStatsMapper = linkLocaleStatsMapper;
        this.linkAccessLogsMapper = linkAccessLogsMapper;
        this.amapExecutor = amapExecutor;
    }

    public void submit(String fullShortUrl, Date accessDate, String ip, Long accessLogId) {
        amapExecutor.execute(new LocaleEnrichTask(fullShortUrl, accessDate, ip, accessLogId));
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

    public final class LocaleEnrichTask implements Runnable {

        private final String fullShortUrl;
        private final Date accessDate;
        private final String ip;
        private final Long accessLogId;

        public LocaleEnrichTask(String fullShortUrl, Date accessDate, String ip, Long accessLogId) {
            this.fullShortUrl = fullShortUrl;
            this.accessDate = accessDate;
            this.ip = ip;
            this.accessLogId = accessLogId;
        }

        @Override
        public void run() {
            try {
                IpLocation location = ipLocationService.resolveRemote(ip);
                persistLocale(fullShortUrl, accessDate, location);
                if (accessLogId != null && !location.isUnknown()) {
                    linkAccessLogsMapper.updateLocale(accessLogId, location.display());
                }
            } catch (Exception ex) {
                log.warn("异步补全地区失败, ip={}, url={}", ip, fullShortUrl, ex);
                persistUnknown();
            }
        }

        public void persistUnknown() {
            persistLocale(fullShortUrl, accessDate, IpLocation.unknown());
        }
    }
}
