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

package com.xhy.shortlink.biz.api.project.constant;

/**
 * 短链接跳转缓存 Redis key 与失效广播 destination。
 * <p>
 * project / risk 必须共用这一份，避免写删 key 或 MQ tag 不一致。
 */
public final class ShortLinkGotoCacheKeys {

    private ShortLinkGotoCacheKeys() {
    }

    /** 跳转缓存，末尾冒号与历史 key 保持一致 */
    public static final String GOTO = "short-link:goto:%s:";

    /** 空值缓存 */
    public static final String GOTO_IS_NULL = "short-link:goto:is-null:%s:";

    /** 本地 Caffeine 失效广播 Topic */
    public static final String INVALIDATE_TOPIC = "short_link_project_cache_invalidate_topic";

    /** 本地 Caffeine 失效广播 Tag，消费者按此过滤 */
    public static final String INVALIDATE_TAG = "invalidate";

    public static String gotoKey(String fullShortUrl) {
        return String.format(GOTO, fullShortUrl);
    }

    public static String gotoIsNullKey(String fullShortUrl) {
        return String.format(GOTO_IS_NULL, fullShortUrl);
    }

    /**
     * RocketMQ Spring 的 destination：{@code topic:tag}
     */
    public static String invalidateDestination() {
        return INVALIDATE_TOPIC + ":" + INVALIDATE_TAG;
    }
}
