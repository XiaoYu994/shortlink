package com.xhy.shortlink.project.mq.producer;

import com.xhy.shortlink.project.mq.event.ShortLinkStatsRecordEvent;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.DELAY_QUEUE_STATS_KEY;


/*
 * 延迟消费短链接统计发送者
 */
@Deprecated
@RequiredArgsConstructor
public class DelayShortLinkStatsProducer {
    private final RedissonClient redissonClient;
    /**
     * 发送延迟消费短链接统计
     *
     * @param statsRecord 短链接统计实体参数
     */
    public void send(ShortLinkStatsRecordEvent statsRecord) {
        RBlockingDeque<ShortLinkStatsRecordEvent> blockingDeque = redissonClient.getBlockingDeque(DELAY_QUEUE_STATS_KEY);
        RDelayedQueue<ShortLinkStatsRecordEvent> delayedQueue = redissonClient.getDelayedQueue(blockingDeque);
        delayedQueue.offer(statsRecord, 5, TimeUnit.SECONDS);
    }

}
