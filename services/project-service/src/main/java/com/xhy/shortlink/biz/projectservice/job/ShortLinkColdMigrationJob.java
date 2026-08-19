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

package com.xhy.shortlink.biz.projectservice.job;

import com.xhy.shortlink.biz.projectservice.service.ShortLinkColdDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 冷数据定时迁移任务
 * <p>
 * 每日凌晨扫描热表，将超过指定天数未访问的链接迁移到冷库。
 *
 * @author XiaoYu
 */
@Component
@RequiredArgsConstructor
public class ShortLinkColdMigrationJob {

    private final ShortLinkColdDataService shortLinkColdDataService;

    @Scheduled(cron = "${short-link.cold-data.cron:0 30 2 * * ?}")
    public void migrateColdLinks() {
        shortLinkColdDataService.migrateInactiveLinks();
    }
}
