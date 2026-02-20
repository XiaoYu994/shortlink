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

package com.xhy.shortlink.biz.projectservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 冷数据管理配置属性
 *
 * @author XiaoYu
 */
@Data
@ConfigurationProperties(prefix = "short-link.cold-data")
public class ColdDataProperties {

    /** 是否启用冷数据迁移 */
    private Boolean enabled = true;

    /** 不活跃天数阈值，超过则迁移到冷库 */
    private Integer days = 90;

    /** 每批迁移数量 */
    private Integer batchSize = 200;

    /** 定时任务 cron 表达式 */
    private String cron = "0 30 2 * * ?";

    /** 回温配置 */
    private Rehot rehot = new Rehot();

    /** 过期宽限期（天） */
    private Integer graceDays = 30;

    @Data
    public static class Rehot {
        /** 回温访问次数阈值 */
        private Integer threshold = 1000;
    }
}
