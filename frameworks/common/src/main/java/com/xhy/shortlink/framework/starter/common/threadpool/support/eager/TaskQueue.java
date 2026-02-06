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

package com.xhy.shortlink.framework.starter.common.threadpool.support.eager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 急切线程池任务队列
 * <p>
 * 当线程数未达到最大线程数时，优先创建新线程而非放入队列
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {

    private EagerThreadPoolExecutor executor;

    /**
     * 初始化任务队列并指定容量上限
     */
    public TaskQueue(int capacity) {
        super(capacity);
    }

    /**
     * 绑定所属的线程池实例，使队列能感知线程池当前状态
     */
    public void setExecutor(EagerThreadPoolExecutor executor) {
        this.executor = executor;
    }

    /**
     * 重写入队逻辑，实现"急切创建线程"策略：
     * 1. 已提交任务数 < 当前线程数 → 有空闲线程，正常入队
     * 2. 当前线程数 < 最大线程数 → 返回 false，触发线程池创建新线程
     * 3. 线程数已达上限 → 正常入队等待
     */
    @Override
    public boolean offer(Runnable runnable) {
        int currentPoolSize = executor.getPoolSize();
        // 如果有空闲线程，直接放入队列让空闲线程处理
        if (executor.getSubmittedTaskCount() < currentPoolSize) {
            return super.offer(runnable);
        }
        // 如果当前线程数小于最大线程数，返回 false 触发创建新线程
        if (currentPoolSize < executor.getMaximumPoolSize()) {
            return false;
        }
        // 线程数已达上限，放入队列等待
        return super.offer(runnable);
    }

    /**
     * 任务被拒绝后的重试入队，带超时等待。若线程池已关闭则直接抛出拒绝异常
     */
    public boolean retryOffer(Runnable runnable, long timeout, TimeUnit unit) throws InterruptedException {
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("Executor is shutdown");
        }
        return super.offer(runnable, timeout, unit);
    }
}
