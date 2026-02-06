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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 对象转换工具类
 * <p>
 * 基于 Spring BeanUtils 实现浅拷贝对象转换
 */
public final class BeanUtil {

    private BeanUtil() {
    }

    /**
     * 将源对象转换为目标类型
     *
     * @param source      源对象
     * @param targetClass 目标类型
     * @param <T>         目标泛型
     * @return 目标对象，source 为 null 时返回 null
     */
    public static <T> T convert(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            // 通过无参构造器反射创建目标对象，再通过 Spring BeanUtils 浅拷贝同名属性
            T target = targetClass.getDeclaredConstructor().newInstance();
            org.springframework.beans.BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Bean convert error", e);
        }
    }

    /**
     * 将源对象列表转换为目标类型列表
     *
     * @param sourceList  源对象列表
     * @param targetClass 目标类型
     * @param <T>         目标泛型
     * @return 目标对象列表
     */
    public static <T> List<T> convert(List<?> sourceList, Class<T> targetClass) {
        // 对列表中每个元素逐一调用单对象 convert，空列表返回 null
        return Optional.ofNullable(sourceList)
                .map(list -> list.stream()
                        .map(source -> convert(source, targetClass))
                        .collect(Collectors.toList()))
                .orElse(null);
    }
}
