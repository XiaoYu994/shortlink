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

package com.xhy.shortlink.framework.starter.cache;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

/**
 * 缓存顶层接口，定义基础缓存操作契约
 */
public interface Cache {

    /**
     * 根据 key 获取缓存值并反序列化为指定类型
     */
    <T> T get(@NotBlank String key, Class<T> clazz);

    /**
     * 放入缓存（使用默认超时时间）
     */
    void put(@NotBlank String key, Object value);

    /**
     * 如果 keys 全部不存在，则新增并返回 true，反之返回 false（原子操作）
     */
    Boolean putIfAllAbsent(@NotNull Collection<String> keys);

    /**
     * 删除指定 key 的缓存
     */
    Boolean delete(@NotBlank String key);

    /**
     * 批量删除缓存，返回成功删除的数量
     */
    Long delete(@NotNull Collection<String> keys);

    /**
     * 判断 key 是否存在
     */
    Boolean hasKey(@NotBlank String key);

    /**
     * 获取底层缓存组件实例（如 StringRedisTemplate）
     */
    Object getInstance();
}
