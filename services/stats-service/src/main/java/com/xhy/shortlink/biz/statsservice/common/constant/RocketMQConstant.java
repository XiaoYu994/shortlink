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

package com.xhy.shortlink.biz.statsservice.common.constant;

/**
 * RocketMQ Topic / Group 常量（stats-service 使用）
 */
public final class RocketMQConstant {

    private RocketMQConstant() {
    }

    public static final String STATS_RECORD_TOPIC = "short_link_project_stats_record_topic";
    public static final String STATS_RECORD_GROUP = "short_link_project_stats_record_group";
}
