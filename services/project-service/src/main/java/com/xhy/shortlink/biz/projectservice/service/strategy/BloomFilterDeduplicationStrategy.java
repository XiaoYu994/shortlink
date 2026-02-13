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
import com.xhy.shortlink.biz.projectservice.toolkit.HashUtil;
import com.xhy.shortlink.framework.starter.convention.exception.ServiceException;
import com.xhy.shortlink.framework.starter.designpattern.strategy.AbstractExecuteStrategy;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.springframework.stereotype.Component;

/**
 * 布隆过滤器去重策略
 *
 * <p>通过布隆过滤器快速判断短链接是否已存在，存在误判但性能极高
 *
 * @author XiaoYu
 */
@Component
@RequiredArgsConstructor
public class BloomFilterDeduplicationStrategy implements AbstractExecuteStrategy<String, String> {

    public static final String MARK = "bloom-filter";

    private final RBloomFilter<String> shortlinkUriCreateCachePenetrationBloomFilter;

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
        int customGenerateCount = 0;
        String shortUri;
        StringBuilder originUrlBuilder = new StringBuilder(originUrl);
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接生成频繁，请稍后再试");
            }
            shortUri = HashUtil.hashToBase62(originUrlBuilder);
            if (!shortlinkUriCreateCachePenetrationBloomFilter.contains(domain + "/" + shortUri)) {
                break;
            }
            originUrlBuilder.append(UUID.randomUUID());
            customGenerateCount++;
        }
        return shortUri;
    }
}
