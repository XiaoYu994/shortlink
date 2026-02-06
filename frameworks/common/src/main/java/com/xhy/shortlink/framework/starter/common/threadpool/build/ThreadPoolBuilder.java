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
import com.xhy.shortlink.framework.starter.common.threadpool.proxy.RejectedProxyUtil;
import com.xhy.shortlink.framework.starter.common.threadpool.support.eager.EagerThreadPoolExecutor;
import com.xhy.shortlink.framework.starter.common.threadpool.support.eager.TaskQueue;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池构建器
 */
public final class ThreadPoolBuilder implements Builder<ThreadPoolExecutor> {

    @Serial
    private static final long serialVersionUID = 1L;

    private int corePoolSize = calculateCoreNum();

    private int maximumPoolSize = corePoolSize + (corePoolSize >> 1);

    private long keepAliveTime = 30000L;

    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    private int workQueueCapacity = 4096;

    private RejectedExecutionHandler rejectedExecutionHandler = new ThreadPoolExecutor.AbortPolicy();

    private boolean isDaemon = false;

    private String threadNamePrefix;

    private ThreadFactory threadFactory;

    private boolean isEager = false;

    /**
     * 根据 CPU 核心数计算默认核心线程数，公式：CPU 核心数 / 0.8
     * 适用于混合型任务场景，留出 20% 余量应对上下文切换开销
     */
    private static int calculateCoreNum() {
        int cpuCoreNum = Runtime.getRuntime().availableProcessors();
        return new BigDecimal(cpuCoreNum).divide(new BigDecimal("0.8")).intValue();
    }

    /**
     * 设置核心线程数，默认值由 {@link #calculateCoreNum()} 计算
     */
    public ThreadPoolBuilder corePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this;
    }

    /**
     * 设置最大线程数，默认值为核心线程数的 1.5 倍
     */
    public ThreadPoolBuilder maximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
        return this;
    }

    /**
     * 设置空闲线程存活时间，默认 30000 毫秒
     */
    public ThreadPoolBuilder keepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    /**
     * 设置空闲线程存活时间及时间单位
     */
    public ThreadPoolBuilder keepAliveTime(long keepAliveTime, TimeUnit timeUnit) {
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        return this;
    }

    /**
     * 设置工作队列容量，默认 4096
     */
    public ThreadPoolBuilder workQueueCapacity(int workQueueCapacity) {
        this.workQueueCapacity = workQueueCapacity;
        return this;
    }

    /**
     * 设置拒绝策略，默认 AbortPolicy（直接抛异常）
     */
    public ThreadPoolBuilder rejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
        this.rejectedExecutionHandler = rejectedExecutionHandler;
        return this;
    }

    /**
     * 设置线程是否为守护线程，默认 false
     */
    public ThreadPoolBuilder daemon(boolean isDaemon) {
        this.isDaemon = isDaemon;
        return this;
    }

    /**
     * 设置线程名称前缀，仅在未自定义 threadFactory 时生效
     */
    public ThreadPoolBuilder threadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
        return this;
    }

    /**
     * 设置自定义线程工厂，设置后 daemon 和 threadNamePrefix 配置将被忽略
     */
    public ThreadPoolBuilder threadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * 设置是否使用急切模式，默认 false
     * 急切模式下优先创建新线程处理任务，而非放入队列等待，适合 IO 密集型场景
     */
    public ThreadPoolBuilder eager(boolean isEager) {
        this.isEager = isEager;
        return this;
    }

    /**
     * 构建线程池实例：
     * 1. 若未指定 threadFactory，则通过 ThreadFactoryBuilder 根据 daemon/prefix 构建
     * 2. 通过动态代理包装拒绝策略，增加拒绝事件日志
     * 3. eager 模式返回 EagerThreadPoolExecutor + TaskQueue，普通模式返回标准 ThreadPoolExecutor + LinkedBlockingQueue
     */
    @Override
    public ThreadPoolExecutor build() {
        if (threadFactory == null) {
            ThreadFactoryBuilder factoryBuilder = new ThreadFactoryBuilder().daemon(isDaemon);
            if (threadNamePrefix != null) {
                factoryBuilder.prefix(threadNamePrefix);
            }
            threadFactory = factoryBuilder.build();
        }
        RejectedExecutionHandler handler = RejectedProxyUtil.createProxy(rejectedExecutionHandler);
        if (isEager) {
            TaskQueue taskQueue = new TaskQueue(workQueueCapacity);
            return new EagerThreadPoolExecutor(
                    corePoolSize, maximumPoolSize, keepAliveTime, timeUnit,
                    taskQueue, threadFactory, handler);
        }
        return new ThreadPoolExecutor(
                corePoolSize, maximumPoolSize, keepAliveTime, timeUnit,
                new LinkedBlockingQueue<>(workQueueCapacity), threadFactory, handler);
    }
}
