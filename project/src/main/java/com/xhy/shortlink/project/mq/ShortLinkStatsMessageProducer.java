package com.xhy.shortlink.project.mq;

import java.util.Map;

/**
 * 通用接口，做到再实现类中自动切换实现消息队列的配置
 */
public interface ShortLinkStatsMessageProducer {
    void send(Map<String, String> producerMap);
}
