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

import lombok.extern.slf4j.Slf4j;

/**
 * 线程工具类
 */
@Slf4j
public final class ThreadUtil {

    private ThreadUtil() {
    }

    /**
     * 线程休眠指定毫秒数，捕获中断异常后恢复中断标志位并记录警告日志
     *
     * @param millis 休眠时间（毫秒）
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // 恢复中断标志位，让上层调用者能感知到中断事件
            Thread.currentThread().interrupt();
            log.warn("Thread sleep interrupted", e);
        }
    }

    /**
     * 创建新线程，设置名称和守护属性，但不启动
     *
     * @param runnable 线程执行体
     * @param name     线程名称
     * @param daemon   是否为守护线程
     * @return 创建好的线程实例（未启动）
     */
    public static Thread newThread(Runnable runnable, String name, boolean daemon) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(daemon);
        return thread;
    }

    /**
     * 创建并立即启动新线程
     *
     * @param runnable 线程执行体
     * @param name     线程名称
     * @param daemon   是否为守护线程
     * @return 已启动的线程实例
     */
    public static Thread startThread(Runnable runnable, String name, boolean daemon) {
        Thread thread = newThread(runnable, name, daemon);
        thread.start();
        return thread;
    }
}
