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

package com.xhy.shortlink.framework.starter.distributedid.core.serviceid;

import com.xhy.shortlink.framework.starter.distributedid.core.IdGenerator;
import com.xhy.shortlink.framework.starter.distributedid.core.snowflake.SnowflakeIdInfo;

/**
 * 业务 ID 生成器接口
 * <p>
 * 继承 IdGenerator，扩展支持传入 serviceId 生成带业务基因的 ID
 */
public interface ServiceIdGenerator extends IdGenerator {

    /**
     * 根据 serviceId 生成雪花算法 ID（基因法）
     */
    default long nextId(long serviceId) {
        return 0L;
    }

    /**
     * 根据 serviceId 生成雪花算法 ID（基因法）
     */
    default long nextId(String serviceId) {
        return 0L;
    }

    /**
     * 根据 serviceId 生成字符串类型雪花算法 ID
     */
    default String nextIdStr(long serviceId) {
        return null;
    }

    /**
     * 根据 serviceId 生成字符串类型雪花算法 ID
     */
    default String nextIdStr(String serviceId) {
        return null;
    }

    /**
     * 解析雪花算法 ID 为各组成部分
     */
    SnowflakeIdInfo parseSnowflakeId(long snowflakeId);
}
