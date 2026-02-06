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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 急切线程池
 * <p>
 * 优先创建线程而非放入队列，适合 IO 密集型场景
 */
public class EagerThreadPoolExecutor extends ThreadPoolExecutor {

    private final AtomicInteger submittedTaskCount = new AtomicInteger(0);

    /**
     * 构造急切线程池，并将自身注入 TaskQueue 以便队列感知线程池状态
     */
    public EagerThreadPoolExecutor(int corePoolSize,
                                   int maximumPoolSize,
                                   long keepAliveTime,
                                   TimeUnit unit,
                                   TaskQueue workQueue,
                                   ThreadFactory threadFactory,
                                   RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        workQueue.setExecutor(this);
    }

    /**
     * 获取当前已提交但尚未完成的任务数，用于 TaskQueue 判断是否有空闲线程
     */
    public int getSubmittedTaskCount() {
        return submittedTaskCount.get();
    }

    /**
     * 任务执行完毕后回调，递减已提交任务计数器
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        submittedTaskCount.decrementAndGet();
    }

    /**
     * 提交任务执行：
     * 1. 先递增已提交任务计数
     * 2. 调用父类 execute，由 TaskQueue.offer 决定是创建新线程还是入队
     * 3. 若被拒绝，尝试通过 retryOffer 重新放入队列（兜底）
     * 4. 任何异常路径都递减计数器，保证计数准确
     */
    @Override
    public void execute(Runnable command) {
        submittedTaskCount.incrementAndGet();
        try {
            super.execute(command);
        } catch (RejectedExecutionException ex) {
            // 被拒绝后尝试重新放入队列
            TaskQueue taskQueue = (TaskQueue) getQueue();
            try {
                if (!taskQueue.retryOffer(command, 0, TimeUnit.MILLISECONDS)) {
                    submittedTaskCount.decrementAndGet();
                    throw new RejectedExecutionException("Queue capacity is full.", ex);
                }
            } catch (InterruptedException iex) {
                submittedTaskCount.decrementAndGet();
                throw new RejectedExecutionException(iex);
            }
        } catch (Exception ex) {
            submittedTaskCount.decrementAndGet();
            throw ex;
        }
    }
}
