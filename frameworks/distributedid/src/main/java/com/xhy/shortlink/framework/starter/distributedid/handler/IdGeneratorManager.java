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

package com.xhy.shortlink.framework.starter.distributedid.handler;

import com.xhy.shortlink.framework.starter.distributedid.core.IdGenerator;
import com.xhy.shortlink.framework.starter.distributedid.core.serviceid.DefaultServiceIdGenerator;
import com.xhy.shortlink.framework.starter.distributedid.core.serviceid.ServiceIdGenerator;
import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ID 生成器管理器
 * <p>
 * 通过 ConcurrentHashMap 管理多个 IdGenerator 实例，
 * 支持按 resource 名称注册和获取不同的生成器
 */
public final class IdGeneratorManager {

    private static final Map<String, IdGenerator> MANAGER = new ConcurrentHashMap<>();

    /*
       注册默认的业务 ID 生成器
     */
    static {
        MANAGER.put("default", new DefaultServiceIdGenerator());
    }

    /**
     * 注册 ID 生成器，已存在则跳过
     */
    public static void registerIdGenerator(@NonNull String resource, @NonNull IdGenerator idGenerator) {
        IdGenerator actual = MANAGER.get(resource);
        if (actual != null) {
            return;
        }
        MANAGER.put(resource, idGenerator);
    }

    /**
     * 根据 resource 获取 ID 生成器
     */
    public static ServiceIdGenerator getIdGenerator(@NonNull String resource) {
        return (ServiceIdGenerator) MANAGER.get(resource);
    }

    /**
     * 获取默认业务 ID 生成器
     */
    public static ServiceIdGenerator getDefaultServiceIdGenerator() {
        return (ServiceIdGenerator) MANAGER.get("default");
    }
}
