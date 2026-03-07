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

package com.xhy.shortlink.biz.projectservice.service.strategy;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.toolkit.HashUtil;
import com.xhy.shortlink.framework.starter.convention.exception.ServiceException;
import com.xhy.shortlink.framework.starter.designpattern.strategy.AbstractExecuteStrategy;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.SHORT_LINK_CREATE_LOCK_KEY;

/**
 * 分布式锁去重策略
 *
 * <p>通过 Redisson 分布式锁 + DB 查询精确判断短链接是否已存在，保证唯一性
 *
 * @author XiaoYu
 */
@Component
@RequiredArgsConstructor
public class DistributedLockDeduplicationStrategy implements AbstractExecuteStrategy<String, String> {

    public static final String MARK = "distributed-lock";
    private static final int MAX_GENERATE_RETRY = 10;

    private final RedissonClient redissonClient;
    private final ShortLinkMapper shortLinkMapper;

    @Override
    public String mark() {
        return MARK;
    }

    /**
     * 生成不重复的短链接后缀
     *
     * @param requestParam 格式为 "originUrl|domain"
     * @return 短链接后缀
     */
    @Override
    public String executeResp(String requestParam) {
        String[] parts = requestParam.split("\\|", 2);
        String originUrl = parts[0];
        String domain = parts[1];
        RLock lock = redissonClient.getLock(SHORT_LINK_CREATE_LOCK_KEY);
        lock.lock();
        try {
            int customGenerateCount = 0;
            String shortUri;
            while (true) {
                if (customGenerateCount > MAX_GENERATE_RETRY) {
                    throw new ServiceException("短链接频繁生成，请稍后再试");
                }
                StringBuilder originUrlBuilder = new StringBuilder(originUrl);
                originUrlBuilder.append(UUID.randomUUID());
                shortUri = HashUtil.hashToBase62(originUrlBuilder);
                ShortLinkDO existing = shortLinkMapper.selectOne(
                        Wrappers.lambdaQuery(ShortLinkDO.class)
                                .eq(ShortLinkDO::getFullShortUrl, domain + "/" + shortUri)
                                .eq(ShortLinkDO::getDelFlag, 0));
                if (existing == null) {
                    break;
                }
                customGenerateCount++;
            }
            return shortUri;
        } finally {
            lock.unlock();
        }
    }
}
