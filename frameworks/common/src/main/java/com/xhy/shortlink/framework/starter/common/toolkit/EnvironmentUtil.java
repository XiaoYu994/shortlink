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

package com.xhy.shortlink.framework.starter.common.toolkit;

import com.xhy.shortlink.framework.starter.bases.ApplicationContextHolder;

import java.util.Arrays;

/**
 * 环境工具类
 */
public final class EnvironmentUtil {

    private static final String DEV_PROFILE = "dev";
    private static final String PROD_PROFILE = "prod";

    private EnvironmentUtil() {
    }

    /**
     * 从 Spring ApplicationContext 中获取当前激活的 profiles 数组
     *
     * @return 激活的 profile 名称数组，未配置时返回空数组
     */
    public static String[] getActiveProfiles() {
        return ApplicationContextHolder.getContext().getEnvironment().getActiveProfiles();
    }

    /**
     * 判断是否为开发环境，检查激活的 profiles 中是否包含 "dev"
     *
     * @return true 表示当前为开发环境
     */
    public static boolean isDev() {
        return Arrays.asList(getActiveProfiles()).contains(DEV_PROFILE);
    }

    /**
     * 判断是否为生产环境，检查激活的 profiles 中是否包含 "prod"
     *
     * @return true 表示当前为生产环境
     */
    public static boolean isProd() {
        return Arrays.asList(getActiveProfiles()).contains(PROD_PROFILE);
    }
}
