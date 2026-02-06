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

package com.xhy.shortlink.framework.starter.common.threadpool.build;

import com.xhy.shortlink.framework.stater.designpattern.builder.Builder;

import java.io.Serial;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程工厂构建器
 */
public final class ThreadFactoryBuilder implements Builder<ThreadFactory> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ThreadFactory backingThreadFactory;

    private String namePrefix;

    private Boolean daemon;

    private Integer priority;

    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

    /**
     * 设置线程名称前缀，最终线程名格式为 prefix-N（N 为自增序号）
     *
     * @param namePrefix 线程名称前缀
     * @return this
     */
    public ThreadFactoryBuilder prefix(String namePrefix) {
        this.namePrefix = namePrefix;
        return this;
    }

    /**
     * 设置线程是否为守护线程
     *
     * @param daemon true 表示守护线程，JVM 退出时不等待守护线程结束
     * @return this
     */
    public ThreadFactoryBuilder daemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    /**
     * 设置线程优先级，范围 [1, 10]，超出范围抛出 IllegalArgumentException
     *
     * @param priority 线程优先级
     * @return this
     */
    public ThreadFactoryBuilder priority(int priority) {
        if (priority < Thread.MIN_PRIORITY) {
            throw new IllegalArgumentException(
                    String.format("Thread priority (%d) must be >= %d", priority, Thread.MIN_PRIORITY));
        }
        if (priority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    String.format("Thread priority (%d) must be <= %d", priority, Thread.MAX_PRIORITY));
        }
        this.priority = priority;
        return this;
    }

    /**
     * 设置线程未捕获异常处理器
     *
     * @param uncaughtExceptionHandler 异常处理器
     * @return this
     */
    public ThreadFactoryBuilder uncaughtExceptionHandler(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
        return this;
    }

    /**
     * 设置底层线程工厂，未设置时使用 {@link Executors#defaultThreadFactory()}
     *
     * @param backingThreadFactory 底层线程工厂
     * @return this
     */
    public ThreadFactoryBuilder threadFactory(ThreadFactory backingThreadFactory) {
        this.backingThreadFactory = backingThreadFactory;
        return this;
    }

    /**
     * 构建线程工厂实例，委托给 buildInternal 执行实际构建逻辑
     *
     * @return 配置好的 ThreadFactory 实例
     */
    @Override
    public ThreadFactory build() {
        return buildInternal(this);
    }

    /**
     * 内部构建逻辑：
     * 1. 确定底层工厂，未指定则使用 JDK 默认工厂
     * 2. 若设置了 namePrefix，初始化原子计数器用于线程编号
     * 3. 返回 lambda 工厂，每次创建线程时依次应用 name/daemon/priority/handler 配置
     */
    private static ThreadFactory buildInternal(ThreadFactoryBuilder builder) {
        final ThreadFactory backingThreadFactory = builder.backingThreadFactory != null
                ? builder.backingThreadFactory
                : Executors.defaultThreadFactory();
        final AtomicLong count = builder.namePrefix != null ? new AtomicLong() : null;
        return r -> {
            Thread thread = backingThreadFactory.newThread(r);
            if (builder.namePrefix != null) {
                thread.setName(builder.namePrefix + "-" + count.getAndIncrement());
            }
            if (builder.daemon != null) {
                thread.setDaemon(builder.daemon);
            }
            if (builder.priority != null) {
                thread.setPriority(builder.priority);
            }
            if (builder.uncaughtExceptionHandler != null) {
                thread.setUncaughtExceptionHandler(builder.uncaughtExceptionHandler);
            }
            return thread;
        };
    }
}
