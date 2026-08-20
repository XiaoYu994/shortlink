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

package com.xhy.shortlink.biz.projectservice.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.mq.event.UpdateFaviconEvent;
import com.xhy.shortlink.biz.projectservice.toolkit.FaviconService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 创建/修改短链后异步回写网站图标。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkListener {

    private final FaviconService faviconService;
    private final ShortLinkMapper shortLinkMapper;

    @EventListener
    public void onUpdateFaviconEvent(UpdateFaviconEvent event) {
        if (event == null || !StringUtils.hasText(event.getOriginUrl())) {
            return;
        }
        log.info("监听到图标更新请求: {}", event.getOriginUrl());
        faviconService.getFaviconUrl(event.getOriginUrl()).thenAccept(faviconUrl -> {
            if (!StringUtils.hasText(faviconUrl)) {
                log.info("未解析到图标，保留空值。URL: {}", event.getFullShortUrl());
                return;
            }
            ShortLinkDO updateDO = ShortLinkDO.builder()
                    .favicon(faviconUrl)
                    .build();
            shortLinkMapper.update(updateDO, Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, event.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, event.getGid()));
            log.info("图标更新成功，URL: {}, Icon: {}", event.getFullShortUrl(), faviconUrl);
        }).exceptionally(ex -> {
            log.error("图标更新过程中发生异常, URL: {}", event.getFullShortUrl(), ex);
            return null;
        });
    }
}
