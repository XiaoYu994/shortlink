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

package com.xhy.shortlink.project.mq.consumer;

import com.xhy.shortlink.project.handler.MessageQueueIdempotentHandler;
import com.xhy.shortlink.project.mq.event.ShortLinkStatsRecordEvent;
import com.xhy.shortlink.project.service.ShortLinkService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RedissonClient;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.DELAY_QUEUE_STATS_KEY;

@Slf4j
@Deprecated
@RequiredArgsConstructor
public class DelayShortLinkStatsConsumer {

    private final RedissonClient redissonClient;
    private final ShortLinkService shortLinkService;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;
    private final Executor delayStatsExecutor;

    private volatile boolean running = false;

    @PostConstruct
    public void start() {
        if (running) return;
        running = true;
        // 异步执行，不阻塞 Spring 启动
        delayStatsExecutor.execute(this::onMessage);
        log.info("延迟统计消费者已启动...");
    }

    @PreDestroy
    public void stop() {
        log.info("延迟统计消费者正在停止...");
        running = false;
    }

    public void onMessage() {
        RBlockingDeque<ShortLinkStatsRecordEvent> blockingDeque = redissonClient.getBlockingDeque(DELAY_QUEUE_STATS_KEY);
        // 只要调用即可触发 Redisson 的订阅和调度机制
        redissonClient.getDelayedQueue(blockingDeque);

        while (running) {
            try {
                // 阻塞 500ms 拿消息；无消息就循环
                ShortLinkStatsRecordEvent statsRecord = blockingDeque.poll(500, TimeUnit.MILLISECONDS);
                if (statsRecord != null) {
                    String key = statsRecord.getFullShortUrl();
                    // 2. 幂等性检查逻辑
                    if (messageQueueIdempotentHandler.isMessageBeingConsumed(key)) {
                        // 如果已经完成，直接跳过
                        if (messageQueueIdempotentHandler.isAccomplish(key)) {
                            continue;
                        }
                        // 如果既没在消费中，也没完成，可能是异常状态，记录日志并跳过（或者重新入队，看业务需求）
                        log.warn("消息未完成流程，需要消息队列重试或人工介入: {}", statsRecord);
                        continue;
                    }

                    try {
//                        shortLinkService.shortLinkStats(statsRecord);
                        // 正常处理完，标记完成
                        messageQueueIdempotentHandler.setAccomplish(key);
                    } catch (Throwable e) {
                        // 业务处理异常
                        messageQueueIdempotentHandler.delMessageProcessed(key);
                        log.error("延迟记录短链接监控消费异常", e);
                        // 如果需要重试，可以将 statsRecord 重新加入延迟队列，或者依赖其他补偿机制
                    }
                }
            } catch (InterruptedException e) {
                // 只有被中断（比如服务关闭）时才退出
                log.warn("延迟消费者线程被中断");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("延迟消费者循环发生未知异常", e);
                // 兜底异常捕获，防止未知错误杀死线程
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }
}