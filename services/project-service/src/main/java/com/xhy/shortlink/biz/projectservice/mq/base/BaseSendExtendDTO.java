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

package com.xhy.shortlink.biz.projectservice.mq.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RocketMQ 消息发送扩展属性
 *
 * @author XiaoYu
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BaseSendExtendDTO {

    /** 默认消息发送超时时间（毫秒） */
    public static final long DEFAULT_SEND_TIMEOUT = 2000L;

    /** 事件名称 */
    private String eventName;

    /** 主题 */
    private String topic;

    /** 标签 */
    private String tag;

    /** 业务标识 */
    private String keys;

    /** 发送消息超时时间（毫秒） */
    @Builder.Default
    private Long sentTimeout = DEFAULT_SEND_TIMEOUT;

    /** 延迟时间（毫秒） */
    private Long delayTime;

    /** 消息发送类型 */
    @Builder.Default
    private SendType sendType = SendType.SYNC;

    /**
     * 消息发送类型枚举
     */
    public enum SendType {
        /** 同步 */
        SYNC,
        /** 异步 */
        ASYNC,
        /** 单向（只管发，不等待结果） */
        ONEWAY
    }
}
