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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

/**
 * 随机 WorkId 选择策略
 * <p>
 * 通过随机数生成 WorkId 和 DatacenterId，
 * 适合单机或测试环境，分布式环境下存在 ID 冲突风险
 */
@Slf4j
public class RandomWorkIdChoose extends AbstractWorkIdChooseTemplate implements InitializingBean {

    /**
     * WorkId 和 DataCenterId 的最大值（5 位，0~31）
     */
    private static final int MAX_ID = 31;

    @Override
    protected WorkIdWrapper chooseWorkId() {
        return new WorkIdWrapper(getRandom(0, MAX_ID), getRandom(0, MAX_ID));
    }

    /**
     * Bean 初始化回调，触发模板方法完成 Snowflake 初始化
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        chooseAndInit();
    }

    private static long getRandom(int start, int end) {
        return (long) (Math.random() * (end - start + 1) + start);
    }
}
