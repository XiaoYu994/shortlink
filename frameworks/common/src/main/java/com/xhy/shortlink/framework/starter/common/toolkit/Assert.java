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

import com.xhy.shortlink.framework.starter.convention.exception.ClientException;

import java.util.Collection;

/**
 * 断言工具类
 */
public final class Assert {

    private Assert() {
    }

    /**
     * 断言对象不为 null，为 null 时抛出 ClientException
     *
     * @param obj     待检查对象
     * @param message 异常提示信息
     */
    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new ClientException(message);
        }
    }

    /**
     * 断言字符串不为空（null 或 ""），不满足时抛出 ClientException
     *
     * @param str     待检查字符串
     * @param message 异常提示信息
     */
    public static void notEmpty(String str, String message) {
        if (str == null || str.isEmpty()) {
            throw new ClientException(message);
        }
    }

    /**
     * 断言集合不为空（null 或 size=0），不满足时抛出 ClientException
     *
     * @param collection 待检查集合
     * @param message    异常提示信息
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new ClientException(message);
        }
    }

    /**
     * 断言条件为 true，不满足时抛出 ClientException
     *
     * @param expression 布尔表达式
     * @param message    异常提示信息
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new ClientException(message);
        }
    }

    /**
     * 断言字符串不为 blank（null、""、全空白字符），不满足时抛出 ClientException
     *
     * @param str     待检查字符串
     * @param message 异常提示信息
     */
    public static void notBlank(String str, String message) {
        if (str == null || str.isBlank()) {
            throw new ClientException(message);
        }
    }
}
