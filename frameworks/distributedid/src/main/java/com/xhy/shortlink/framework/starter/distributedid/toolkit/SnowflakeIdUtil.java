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

package com.xhy.shortlink.framework.starter.distributedid.toolkit;

import com.xhy.shortlink.framework.starter.distributedid.core.snowflake.Snowflake;
import com.xhy.shortlink.framework.starter.distributedid.core.snowflake.SnowflakeIdInfo;
import com.xhy.shortlink.framework.starter.distributedid.handler.IdGeneratorManager;

/**
 * 雪花算法 ID 工具类
 * <p>
 * 提供静态方法便捷生成 ID，支持普通雪花 ID 和带业务基因的 ID
 */
public final class SnowflakeIdUtil {

    private static Snowflake SNOWFLAKE;

    /**
     * 初始化雪花算法实例（由 AbstractWorkIdChooseTemplate 调用）
     */
    public static void initSnowflake(Snowflake snowflake) {
        SnowflakeIdUtil.SNOWFLAKE = snowflake;
    }

    /**
     * 获取雪花算法实例
     */
    public static Snowflake getInstance() {
        return SNOWFLAKE;
    }

    /**
     * 生成下一个雪花 ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 生成下一个字符串类型雪花 ID
     */
    public static String nextIdStr() {
        return Long.toString(nextId());
    }

    /**
     * 反解析雪花 ID
     */
    public static SnowflakeIdInfo parseSnowflakeId(String snowflakeId) {
        return SNOWFLAKE.parseSnowflakeId(Long.parseLong(snowflakeId));
    }

    /**
     * 反解析雪花 ID
     */
    public static SnowflakeIdInfo parseSnowflakeId(long snowflakeId) {
        return SNOWFLAKE.parseSnowflakeId(snowflakeId);
    }

    /**
     * 根据 serviceId 生成带业务基因的雪花 ID
     */
    public static long nextIdByService(String serviceId) {
        return IdGeneratorManager.getDefaultServiceIdGenerator()
                .nextId(Long.parseLong(serviceId));
    }

    /**
     * 根据 serviceId 生成带业务基因的字符串类型雪花 ID
     */
    public static String nextIdStrByService(String serviceId) {
        return IdGeneratorManager.getDefaultServiceIdGenerator()
                .nextIdStr(Long.parseLong(serviceId));
    }

    /**
     * 根据 resource 和 serviceId 生成字符串类型雪花 ID
     */
    public static String nextIdStrByService(String resource, long serviceId) {
        return IdGeneratorManager.getIdGenerator(resource)
                .nextIdStr(serviceId);
    }

    /**
     * 根据 resource 和 serviceId 生成字符串类型雪花 ID
     */
    public static String nextIdStrByService(String resource, String serviceId) {
        return IdGeneratorManager.getIdGenerator(resource)
                .nextIdStr(serviceId);
    }

    /**
     * 反解析带业务基因的雪花 ID
     */
    public static SnowflakeIdInfo parseSnowflakeServiceId(String snowflakeId) {
        return IdGeneratorManager.getDefaultServiceIdGenerator()
                .parseSnowflakeId(Long.parseLong(snowflakeId));
    }

    /**
     * 根据 resource 反解析带业务基因的雪花 ID
     */
    public static SnowflakeIdInfo parseSnowflakeServiceId(String resource, String snowflakeId) {
        return IdGeneratorManager.getIdGenerator(resource)
                .parseSnowflakeId(Long.parseLong(snowflakeId));
    }
}
