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

package com.xhy.shortlink.framework.starter.common.threadpool.proxy;

import java.lang.reflect.Proxy;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * 拒绝策略代理工具类
 * <p>
 * 通过动态代理增强拒绝策略，添加日志记录
 */
public final class RejectedProxyUtil {

    private RejectedProxyUtil() {
    }


    /**
     * 通过 JDK 动态代理包装原始拒绝策略，代理对象在执行拒绝逻辑前会记录日志和计数
     *
     * @param handler 原始拒绝策略
     * @return 增强后的拒绝策略代理
     */
    public static RejectedExecutionHandler createProxy(RejectedExecutionHandler handler) {
        return (RejectedExecutionHandler) Proxy.newProxyInstance(
                handler.getClass().getClassLoader(),
                new Class[]{RejectedExecutionHandler.class},
                new RejectedProxyInvocationHandler(handler));
    }
}
