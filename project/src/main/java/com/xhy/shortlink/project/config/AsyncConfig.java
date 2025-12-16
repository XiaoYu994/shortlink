package com.xhy.shortlink.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 1. 原有的爬虫线程池
     * 特点：IO 密集型，需要较多线程去下载图片
     */
    @Bean("crawlerExecutor") // 名字和 @Async("crawlerExecutor") 对应
    public Executor crawlerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);  // 核心线程数
        executor.setMaxPoolSize(20);   // 最大线程数
        executor.setQueueCapacity(500); // 队列容量
        executor.setThreadNamePrefix("Favicon-Crawler-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 2. 新增：延迟队列统计消费线程池
     * 特点：CPU/DB 密集型，这是一个长驻任务（while true），不需要太多线程，但要求极其稳定
     */
    @Bean("delayStatsExecutor")
    public Executor delayStatsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：根据你的消费者数量定。
        // 如果你只启动一个 while(true) 循环，设置为 1 即可。
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Delay-Stats-Consumer-");

        // 【关键配置】优雅停机
        // 确保在 Spring 容器关闭时，等待当前任务处理完再销毁线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60); // 最多等待 60 秒

        executor.initialize();
        return executor;
    }
}
