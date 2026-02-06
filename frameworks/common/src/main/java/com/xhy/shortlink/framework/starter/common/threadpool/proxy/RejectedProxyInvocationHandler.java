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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * 线程池拒绝策略代理调用处理器
 * <p>
 * 在原有拒绝策略基础上增加日志记录
 */
@Slf4j
public class RejectedProxyInvocationHandler implements InvocationHandler {

    private final Object target;

    private final AtomicLong rejectedCount;

    /**
     * 初始化代理处理器，包装原始拒绝策略并初始化拒绝计数器
     *
     * @param target 被代理的原始拒绝策略实例
     */
    public RejectedProxyInvocationHandler(Object target) {
        this.target = target;
        this.rejectedCount = new AtomicLong(0);
    }

    /**
     * 拦截拒绝策略方法调用：
     * 1. 递增拒绝计数器
     * 2. 记录 error 日志（策略类名 + 累计拒绝次数）
     * 3. 委托给原始拒绝策略执行实际逻辑
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        rejectedCount.incrementAndGet();
        log.error("Thread pool rejected task, policy: {}, rejected count: {}",
                target.getClass().getSimpleName(), rejectedCount.get());
        return method.invoke(target, args);
    }
}
