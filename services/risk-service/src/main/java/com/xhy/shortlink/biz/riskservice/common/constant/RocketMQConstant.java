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

package com.xhy.shortlink.biz.riskservice.common.constant;

/**
 * RocketMQ Topic / Group 常量（risk-service 使用）
 */
public final class RocketMQConstant {

    private RocketMQConstant() {
    }

    /** AI 风控审核消息 Topic */
    public static final String RISK_CHECK_TOPIC = "short_link_project_risk_check_topic";

    /** AI 风控审核消费者组 */
    public static final String RISK_CHECK_GROUP = "short_link_project_risk_check_group";

    /** 违规通知 Topic */
    public static final String NOTIFY_TOPIC = "short_link_project_notify_topic";

    /** 违规通知消费者组 */
    public static final String NOTIFY_GROUP = "short_link_project_notify_group";

    /** 清除本地缓存 Topic */
    public static final String CACHE_INVALIDATE_TOPIC = "short_link_project_cache_invalidate_topic";
}
