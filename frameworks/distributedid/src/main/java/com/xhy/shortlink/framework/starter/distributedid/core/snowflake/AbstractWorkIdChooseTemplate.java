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

package com.xhy.shortlink.framework.starter.distributedid.core.snowflake;

import com.xhy.shortlink.framework.starter.distributedid.toolkit.SnowflakeIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * WorkId 选择模板基类（模板方法模式）
 * <p>
 * 子类实现 chooseWorkId() 返回 WorkIdWrapper，
 * chooseAndInit() 负责构造 Snowflake 并注册到 SnowflakeIdUtil
 */
@Slf4j
public abstract class AbstractWorkIdChooseTemplate {

    @Value("${framework.distributed.id.snowflake.is-use-system-clock:false}")
    private boolean isUseSystemClock;

    /**
     * 由子类实现的 WorkId 选择策略
     */
    protected abstract WorkIdWrapper chooseWorkId();

    /**
     * 模板方法：选择 WorkId 并初始化 Snowflake
     * 1. 调用子类策略获取 WorkIdWrapper
     * 2. 构造 Snowflake 实例
     * 3. 注册到 SnowflakeIdUtil 全局静态引用
     */
    public void chooseAndInit() {
        WorkIdWrapper workIdWrapper = chooseWorkId();
        long workId = workIdWrapper.getWorkId();
        long dataCenterId = workIdWrapper.getDataCenterId();
        Snowflake snowflake = new Snowflake(workId, dataCenterId, isUseSystemClock);
        log.info("Snowflake type: {}, workId: {}, dataCenterId: {}",
                this.getClass().getSimpleName(), workId, dataCenterId);
        SnowflakeIdUtil.initSnowflake(snowflake);
    }
}
