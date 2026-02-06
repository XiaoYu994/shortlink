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

package com.xhy.shortlink.framework.starter.bases;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 单例对象容器
 * <p>
 * 提供线程安全的单例对象容器，适用于不适合放入 Spring 容器的对象，
 * 如第三方库对象、延迟初始化的单例对象等。
 */
public class Singleton {

    private static final ConcurrentHashMap<String,Object> SINGLE_OBJECT_POOL = new ConcurrentHashMap<>();

    /*
    *  获取单例对象
    * */
    public static <T> T get(String key) {
        Object result = SINGLE_OBJECT_POOL.get(key);
        return result == null ? null : (T) result;
    }

    /*
    *  获取单例对象，不存在时通过 Supplier 创建
    * */
    public static <T> T get(String key, Supplier<T> supplier) {
        return (T) SINGLE_OBJECT_POOL.computeIfAbsent(key, k ->
            supplier.get());
    }

    /*
    * */
    public static void put(String key, Object value) {
        SINGLE_OBJECT_POOL.put(key, value);
    }

    /*
    *  放入单例对象，使用类名作为 key
    * */
    public static void put(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");        
        }
        put(value.getClass().getName(), value);
    }
}
