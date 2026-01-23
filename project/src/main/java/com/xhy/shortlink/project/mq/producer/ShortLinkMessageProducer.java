package com.xhy.shortlink.project.mq.producer;


/**
 * 通用消息生产者接口
 * @param <T> 消息体类型
 */
public interface ShortLinkMessageProducer<T> {
    void send(T event);
}
