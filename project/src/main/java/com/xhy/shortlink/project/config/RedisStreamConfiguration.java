package com.xhy.shortlink.project.config;

import com.xhy.shortlink.project.mq.consumer.redis.ShortLinkRiskRedisConsumer;
import com.xhy.shortlink.project.mq.consumer.redis.ShortLinkStatsSaveRedisConsumer;
import com.xhy.shortlink.project.mq.consumer.redis.ShortLinkViolationNotifyRedisConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.*;

/*
 * Redis Stream 消息队列配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "short-link.message-queue", name = "implement", havingValue = "Redis")
public class RedisStreamConfiguration {

    private final RedisConnectionFactory redisConnectionFactory;
    // 注入统计消费者
    private final ShortLinkStatsSaveRedisConsumer shortLinkStatsRedisSaveConsumer;
    // 注入 AI 风控消费者
    private final ShortLinkRiskRedisConsumer shortLinkRiskRedisConsumer;
    // 消息通知消费者
    private final ShortLinkViolationNotifyRedisConsumer shortLinkViolationNotifyRedisConsumer;
    // 注入 StringRedisTemplate 用于初始化 Group
    private final StringRedisTemplate stringRedisTemplate;

    @Bean
    public ExecutorService asyncStreamConsumer() {
        AtomicInteger index = new AtomicInteger();
        //  核心线程数从 1 改为 2 (甚至更多)
        // 因为现在有两个独立的消费者在跑，如果还是 1，会导致任务竞争，一个阻塞另一个
        int processors = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                Math.max(2, processors), // 至少给 2 个线程
                Math.max(2, processors) + 10,
                60,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("stream_consumer_short-link_" + index.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> statsStreamMessageListenerContainer(ExecutorService asyncStreamConsumer) {
        // 1. 设置配置 (TargetType = ShortLinkStatsRecordEvent)
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .batchSize(10)
                        .executor(asyncStreamConsumer)
                        .pollTimeout(Duration.ofSeconds(3))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);

        // 2. 注册【监控统计】消费者
        initStreamGroup(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_STREAM_GROUP_KEY);
        StreamMessageListenerContainer.StreamReadRequest<String> readRequest =
                StreamMessageListenerContainer.StreamReadRequest.builder(StreamOffset.create(SHORT_LINK_STATS_STREAM_TOPIC_KEY, ReadOffset.lastConsumed()))
                        .cancelOnError(throwable -> false)
                        .consumer(Consumer.from(SHORT_LINK_STATS_STREAM_GROUP_KEY, "stats-consumer"))
                        .autoAcknowledge(true)
                        .build();
        // 绑定：statsReadRequest -> statsConsumer
        listenerContainer.register(readRequest, shortLinkStatsRedisSaveConsumer);


        // 3. 注册【AI 风控】消费者
        initStreamGroup(RISK_CHECK_STREAM_TOPIC_KEY, RISK_CHECK_STREAM_GROUP_KEY);
        StreamMessageListenerContainer.StreamReadRequest<String> riskReadRequest =
                StreamMessageListenerContainer.StreamReadRequest.builder(StreamOffset.create(RISK_CHECK_STREAM_TOPIC_KEY, ReadOffset.lastConsumed()))
                        .cancelOnError(throwable -> false)
                        .consumer(Consumer.from(RISK_CHECK_STREAM_GROUP_KEY, "risk-consumer"))
                        .autoAcknowledge(false) // 风控业务手动 ACK
                        .build();
        // 绑定：riskReadRequest -> riskConsumer
        listenerContainer.register(riskReadRequest, shortLinkRiskRedisConsumer);

        // 4. 注册发送通知消费者
        initStreamGroup(NOTIFY_STREAM_TOPIC_KEY, NOTIFY_STREAM_GROUP_KEY);
        StreamMessageListenerContainer.StreamReadRequest<String> notifyReadRequest =
                StreamMessageListenerContainer.StreamReadRequest.builder(StreamOffset.create(NOTIFY_STREAM_TOPIC_KEY, ReadOffset.lastConsumed()))
                        .cancelOnError(throwable -> false)
                        .consumer(Consumer.from(NOTIFY_STREAM_GROUP_KEY, "notify-consumer"))
                        .autoAcknowledge(true)
                        .build();
        // 绑定：notifyReadRequest -> riskConsumer
        listenerContainer.register(notifyReadRequest, shortLinkViolationNotifyRedisConsumer);
        // 启动容器
        listenerContainer.start();
        return listenerContainer;
    }

    /**
     * 初始化 Stream Group (防止 Group 不存在报错)
     */
    private void initStreamGroup(String key, String group) {
        try {
            // 判断 key 是否存在
            if (!stringRedisTemplate.hasKey(key)) {
                // 如果 Stream 不存在，创建一个空的 Group，这会自动创建 Stream
                stringRedisTemplate.opsForStream().createGroup(key, group);
                log.info("初始化 Redis Stream Group 成功: key={}, group={}", key, group);
            } else {
                // 如果 Stream 存在，判断 Group 是否存在
                // 注意：Redis 没有直接判断 Group 是否存在的 API，通常是通过捕获 createGroup 异常或 groups info 来判断
                // 这里简单处理：尝试创建，如果报错说明已存在，忽略即可
                try {
                    stringRedisTemplate.opsForStream().createGroup(key, group);
                } catch (Exception e) {
                    // Group already exists, ignore
                }
            }
        } catch (Exception e) {
            log.warn("初始化 Redis Stream Group 异常 (可忽略): {}", e.getMessage());
        }
    }
}