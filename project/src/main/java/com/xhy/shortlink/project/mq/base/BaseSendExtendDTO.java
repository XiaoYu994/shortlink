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

package com.xhy.shortlink.project.mq.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 *  消息发送事件基础扩充属性实体
 * */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BaseSendExtendDTO {

    /*
     * 事件名称
     * */
    private String eventName;

    /*
     * 主题
     * */
    private String topic;

    /*
     *  标签
     * */
    private String tag;

    /*
     *  业务标识
     * */
    private String keys;

    /*
     *  发送消息超时时间
     * */
    // 给个默认值，防止 NPE
    @Builder.Default
    private Long sentTimeout = 2000L;

    /*
     *  具体延迟时间
     * */
    private Long delayTime;

    /**
     * 消息发送类型（默认同步）
     * 必须定义成员变量，外部才能 set/get
     */
    @Builder.Default // 如果用 Builder 模式，建议加默认值
    private SendType sendType = SendType.SYNC;

    /**
     * 定义枚举类型
     */
    public enum SendType {
        SYNC,   // 同步
        ASYNC,  // 异步
        ONEWAY  // 单向（只管发，不等待结果，最快但不可靠）
    }
}
