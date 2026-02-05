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

package com.xhy.shortlink.project.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.project.mq.base.BaseSendExtendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;

/*
 * RocketMQ 抽象公共发送消息组件
 * */
@RequiredArgsConstructor
@Slf4j(topic = "CommonSendProduceTemplate")
public abstract class AbstractCommonSendProduceTemplate<T> {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 构建消息发送事件基础扩充属性实体
     *
     * @param messageEvent 消息发送事件
     * @return 扩充属性实体
     */
    protected abstract BaseSendExtendDTO buildBaseSendExtendDTO(T messageEvent);

    /**
     * 构建消息基本参数，请求头、Keys...
     *
     * @param messageEvent      消息发送事件
     * @param baseSendExtendDTO 消息扩充属性实体
     * @return 消息基本参数
     */
    protected abstract Message<?> buildMessage(T messageEvent, BaseSendExtendDTO baseSendExtendDTO);

    /**
     * 默认发送方法（使用默认的日志回调）
     */
    public SendResult sendMessage(T messageEvent) {
        // 调用下面的重载方法，传入 null 表示使用默认回调
        return sendMessage(messageEvent, null);
    }

    /**
     * 消息事件通用发送
     *
     * @param messageEvent 消息发送事件
     * @return 消息发送返回结果 (同步发送返回结果，异步/单向发送返回 null)
     * @param customCallback 自定义回调（仅在 ASYNC 模式生效），如果为 null 则使用默认日志回调
     */
    public SendResult sendMessage(T messageEvent,SendCallback customCallback) {
        final BaseSendExtendDTO baseSendExtendDTO = buildBaseSendExtendDTO(messageEvent);
        SendResult sendResult = null;

        try {
            // 1. 构建 Destination (Topic:Tag)
            final StringBuilder destinationBuilder = StrUtil.builder().append(baseSendExtendDTO.getTopic());
            if (StrUtil.isNotBlank(baseSendExtendDTO.getTag())) {
                destinationBuilder.append(":").append(baseSendExtendDTO.getTag());
            }
            String destination = destinationBuilder.toString();

            // 2. 构建 Message
            Message<?> message = buildMessage(messageEvent, baseSendExtendDTO);

            // 3. 优先处理延迟消息 (RocketMQTemplate 的任意延迟目前主要是同步的，建议保持同步)
            if (baseSendExtendDTO.getDelayTime() != null && baseSendExtendDTO.getDelayTime() > 0) {
                sendResult = rocketMQTemplate.syncSendDelayTimeMills(destination, message, baseSendExtendDTO.getDelayTime());
                handleSuccessLog(baseSendExtendDTO, sendResult);
            }
            // 4. 处理普通消息 (根据 SendType 路由)
            else {
                // 获取发送模式，如果没设置则默认为 SYNC
                BaseSendExtendDTO.SendType sendType = baseSendExtendDTO.getSendType();
                if (sendType == null) sendType = BaseSendExtendDTO.SendType.SYNC;

                switch (sendType) {
                    case ASYNC:
                        // --- 异步发送核心逻辑 ---
                        SendCallback callbackToUse = (customCallback != null)
                                ? customCallback // 1. 如果子类传了，用子类的
                                : new DefaultLogCallback(baseSendExtendDTO, messageEvent); // 2. 没传就用默认打印日志的
                        rocketMQTemplate.asyncSend(destination, message, callbackToUse, baseSendExtendDTO.getSentTimeout());
                        break;

                    case ONEWAY:
                        // --- 单向发送核心逻辑 ---
                        // 只负责发出去，不等待 Broker 确认，没有返回值，也不会触发回调
                        rocketMQTemplate.sendOneWay(destination, message);
                        log.info("[生产者-OneWay] {} - 消息已投递，不等待结果。Keys：{}", baseSendExtendDTO.getEventName(), baseSendExtendDTO.getKeys());
                        break;

                    case SYNC:
                    default:
                        // --- 同步发送核心逻辑 ---
                        sendResult = rocketMQTemplate.syncSend(destination, message, baseSendExtendDTO.getSentTimeout());
                        handleSuccessLog(baseSendExtendDTO, sendResult);
                        break;
                }
            }
        } catch (Throwable ex) {
            // 捕获主线程抛出的异常（如构建失败、网络连接失败、同步发送超时等）
            handleErrorLog(baseSendExtendDTO, messageEvent, ex);
            throw ex;
        }
        // 对于 ASYNC 和 ONEWAY，这里返回的是 null
        return sendResult;
    }
    // 将默认的日志逻辑封装成一个内部类，方便复用
    private class DefaultLogCallback implements SendCallback {
        private final BaseSendExtendDTO dto;
        private final T event;

        public DefaultLogCallback(BaseSendExtendDTO dto, T event) {
            this.dto = dto;
            this.event = event;
        }
        @Override
        public void onSuccess(SendResult sendResult) {
            handleSuccessLog(dto, sendResult);
        }
        @Override
        public void onException(Throwable e) {
            handleErrorLog(dto, event, e);
        }
    }
    // ========== 私有辅助方法：统一日志处理 ==========
    private void handleSuccessLog(BaseSendExtendDTO baseDTO, SendResult sendResult) {
        log.info("[生产者] {} - 发送成功，MsgId：{}，Status：{}，Keys：{}",
                baseDTO.getEventName(),
                sendResult != null ? sendResult.getMsgId() : "N/A",
                sendResult != null ? sendResult.getSendStatus() : "N/A",
                baseDTO.getKeys());
    }
    private void handleErrorLog(BaseSendExtendDTO baseDTO, T messageEvent, Throwable ex) {
        log.error("[生产者] {} - 消息发送失败，Keys：{}，消息体：{}",
                baseDTO.getEventName(),
                baseDTO.getKeys(),
                JSON.toJSONString(messageEvent),
                ex);
    }
}
