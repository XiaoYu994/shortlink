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

package com.xhy.shortlink.biz.projectservice.service;

import java.util.Date;

/**
 * 短链接缓存服务：多级缓存读写、预热、失效与布隆过滤器
 *
 * @author XiaoYu
 */
public interface ShortLinkCacheService {

    /**
     * 缓存预热：写入 Redis + 删除空值缓存 + 加入布隆过滤器
     *
     * @param fullShortUrl 完整短链接
     * @param originUrl    原始链接
     * @param gid          分组标识
     * @param validDate    有效期（null 表示永久）
     */
    void warmUp(String fullShortUrl, String originUrl, String gid, Date validDate);

    /**
     * 重建多级缓存：写入 L1 Caffeine + L2 Redis
     *
     * @param fullShortUrl 完整短链接
     * @param originUrl    原始链接
     * @param gid          分组标识
     * @param validDate    有效期（null 表示永久）
     */
    void rebuildCache(String fullShortUrl, String originUrl, String gid, Date validDate);

    /**
     * 失效跳转缓存：删 Redis、清本机 Caffeine，并广播让其他实例清本地缓存。
     *
     * @param fullShortUrl 完整短链接
     */
    void invalidate(String fullShortUrl);

    /**
     * 清除本地 Caffeine 缓存
     *
     * @param fullShortUrl 完整短链接
     */
    void evictLocalCache(String fullShortUrl);

    /**
     * 添加到布隆过滤器
     *
     * @param fullShortUrl 完整短链接
     */
    void addToBloomFilter(String fullShortUrl);

    /**
     * 检查布隆过滤器是否包含
     *
     * @param fullShortUrl 完整短链接
     * @return 是否可能存在
     */
    boolean bloomFilterContains(String fullShortUrl);
}
