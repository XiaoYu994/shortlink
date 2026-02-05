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

package com.xhy.shortlink.project.mq.producer.redis;


import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.CHANNEL_TOPIC_KEY;

/*
 * 短链接清除本地缓存 Redis 实现 (Pub/Sub 模式)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkCacheRedisProducer implements ShortLinkMessageProducer<String> {
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public void send(String fullShortUrl) {
        try {
            // 2. 使用 convertAndSend 发送广播消息
            // 注意：这不是存数据，而是“喊话”。只有当前在线的消费者能收到。
            stringRedisTemplate.convertAndSend(CHANNEL_TOPIC_KEY, fullShortUrl);


        } catch (Exception e) {
            log.error("[Redis-PubSub] 广播清除消息失败", e);
            // 缓存清除失败通常只影响短暂的数据一致性，可视业务决定是否抛出异常
        }
    }
}
