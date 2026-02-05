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

package com.xhy.shortlink.project.common.constant;

/*
* RocketMQ 常量类
* */
public class RocketMQConstant {

    /**
     *  清楚本地缓存 topic
     */
    public final static String CACHE_INVALIDATE_TOPIC = "short_link_project_cache_invalidate_topic";

    /**
     *  清楚本地缓存 group
     */
    public final static String CACHE_INVALIDATE_GROUP = "short_link_project_cache_invalidate_group";


    /**
     *  清楚本地缓存 tag
     */
    public final static String CACHE_INVALIDATE_TAG = "invalidate";


    /**
     *  短链接监控 topic
     */
    public final static String STATIC_TOPIC = "short_link_project_static_topic";

    /**
     *  短链接监控 group
     */
    public final static String STATIC_GROUP = "short_link_project_static_group";

    /**
     *   AI 风控审核消息 topic
     */
    public final static String RISK_CHECK_TOPIC = "short_link_project_risk_check_topic";

    /**
     *   AI 风控审核消息 group
     */
    public final static String RISK_CHECK_GROUP = "short_link_project_risk_check_group";

    /**
     *   发送用户通知 topic
     */
    public static final String NOTIFY_TOPIC = "short_link_project_notify_topic";

    /**
     *   发送用户通知 group
     */
    public static final String NOTIFY_GROUP = "short_link_project_notify_group";

    /**
     *   过期短链归档 topic
     */
    public static final String EXPIRE_ARCHIVE_TOPIC = "short_link_project_expire_archive_topic";

    /**
     *   过期短链归档 group
     */
    public static final String EXPIRE_ARCHIVE_GROUP = "short_link_project_expire_archive_group";
}
