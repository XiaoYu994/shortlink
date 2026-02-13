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

package com.xhy.shortlink.biz.projectservice.common.constant;

/**
 * RocketMQ Topic / Group 常量
 *
 * @author XiaoYu
 */
public final class RocketMQConstant {

    private RocketMQConstant() {
    }

    /** 清除本地缓存 Topic */
    public static final String CACHE_INVALIDATE_TOPIC = "short_link_project_cache_invalidate_topic";

    /** 清除本地缓存 Tag */
    public static final String CACHE_INVALIDATE_TAG = "invalidate";

    /** AI 风控审核消息 Topic */
    public static final String RISK_CHECK_TOPIC = "short_link_project_risk_check_topic";

    /** 过期短链归档 Topic */
    public static final String EXPIRE_ARCHIVE_TOPIC = "short_link_project_expire_archive_topic";
}
