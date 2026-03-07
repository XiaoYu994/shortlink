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

package com.xhy.shortlink.biz.projectservice.mq.producer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.biz.projectservice.mq.base.BaseSendExtendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;

/**
 * RocketMQ 抽象公共发送消息模板
 *
 * @param <T> 消息体类型
 * @author XiaoYu
 */
@RequiredArgsConstructor
@Slf4j(topic = "CommonSendProduceTemplate")
public abstract class AbstractCommonSendProduceTemplate<T> {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 构建消息发送事件基础扩充属性
     *
     * @param messageEvent 消息发送事件
     * @return 扩充属性实体
     */
    protected abstract BaseSendExtendDTO buildBaseSendExtendDTO(T messageEvent);

    /**
     * 构建消息基本参数
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
        return sendMessage(messageEvent, null);
    }

    /**
     * 消息事件通用发送
     *
     * @param messageEvent   消息发送事件
     * @param customCallback 自定义回调（仅在 ASYNC 模式生效），null 则使用默认日志回调
     * @return 消息发送返回结果（同步发送返回结果，异步/单向发送返回 null）
     */
    public SendResult sendMessage(T messageEvent, SendCallback customCallback) {
        final BaseSendExtendDTO baseSendExtendDTO = buildBaseSendExtendDTO(messageEvent);
        SendResult sendResult = null;
        try {
            final StringBuilder destinationBuilder = StrUtil.builder().append(baseSendExtendDTO.getTopic());
            if (StrUtil.isNotBlank(baseSendExtendDTO.getTag())) {
                destinationBuilder.append(":").append(baseSendExtendDTO.getTag());
            }
            String destination = destinationBuilder.toString();
            Message<?> message = buildMessage(messageEvent, baseSendExtendDTO);

            if (baseSendExtendDTO.getDelayTime() != null && baseSendExtendDTO.getDelayTime() > 0) {
                sendResult = rocketMQTemplate.syncSendDelayTimeMills(destination, message, baseSendExtendDTO.getDelayTime());
                handleSuccessLog(baseSendExtendDTO, sendResult);
            } else {
                BaseSendExtendDTO.SendType sendType = baseSendExtendDTO.getSendType();
                if (sendType == null) {
                    sendType = BaseSendExtendDTO.SendType.SYNC;
                }
                switch (sendType) {
                    case ASYNC:
                        SendCallback callbackToUse = (customCallback != null)
                                ? customCallback
                                : new DefaultLogCallback(baseSendExtendDTO, messageEvent);
                        rocketMQTemplate.asyncSend(destination, message, callbackToUse, baseSendExtendDTO.getSentTimeout());
                        break;
                    case ONEWAY:
                        rocketMQTemplate.sendOneWay(destination, message);
                        log.info("[生产者-OneWay] {} - 消息已投递。Keys：{}", baseSendExtendDTO.getEventName(), baseSendExtendDTO.getKeys());
                        break;
                    case SYNC:
                    default:
                        sendResult = rocketMQTemplate.syncSend(destination, message, baseSendExtendDTO.getSentTimeout());
                        handleSuccessLog(baseSendExtendDTO, sendResult);
                        break;
                }
            }
        } catch (Throwable ex) {
            handleErrorLog(baseSendExtendDTO, messageEvent, ex);
            throw ex;
        }
        return sendResult;
    }

    private class DefaultLogCallback implements SendCallback {
        private final BaseSendExtendDTO dto;
        private final T event;

        DefaultLogCallback(BaseSendExtendDTO dto, T event) {
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
