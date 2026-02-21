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

package com.xhy.shortlink.biz.projectservice.mq.consumer;

import com.xhy.shortlink.biz.projectservice.common.constant.RocketMQConstant;
import com.xhy.shortlink.biz.projectservice.helper.ShortLinkCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 缓存失效广播消费者
 * <p>
 * 广播模式：每个实例都会收到消息，清除本地 Caffeine 缓存。
 *
 * @author XiaoYu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConstant.CACHE_INVALIDATE_TOPIC,
        consumerGroup = RocketMQConstant.CACHE_INVALIDATE_GROUP,
        selectorExpression = RocketMQConstant.CACHE_INVALIDATE_TAG,
        messageModel = MessageModel.BROADCASTING
)
public class ShortLinkCacheConsumer implements RocketMQListener<String> {

    private final ShortLinkCacheHelper cacheHelper;

    @Override
    public void onMessage(String fullShortUrl) {
        log.info("[MQ广播] 接收到缓存清除消息，目标：{}", fullShortUrl);
        cacheHelper.evictLocalCache(fullShortUrl);
    }
}
