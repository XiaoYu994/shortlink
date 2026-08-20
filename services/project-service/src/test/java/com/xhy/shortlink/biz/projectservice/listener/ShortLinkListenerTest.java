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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.mq.event.UpdateFaviconEvent;
import com.xhy.shortlink.biz.projectservice.toolkit.FaviconService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkListenerTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ShortLinkDO.class);
    }

    @Mock
    private FaviconService faviconService;
    @Mock
    private ShortLinkMapper shortLinkMapper;

    @InjectMocks
    private ShortLinkListener listener;

    @Test
    void writesFaviconWhenResolved() {
        when(faviconService.getFaviconUrl("https://www.baidu.com"))
                .thenReturn(CompletableFuture.completedFuture("https://www.baidu.com/favicon.ico"));

        listener.onUpdateFaviconEvent(UpdateFaviconEvent.builder()
                .fullShortUrl("localhost/abc")
                .gid("g1")
                .originUrl("https://www.baidu.com")
                .build());

        verify(shortLinkMapper).update(
                argThat(entity -> entity instanceof ShortLinkDO
                        && "https://www.baidu.com/favicon.ico".equals(((ShortLinkDO) entity).getFavicon())),
                any());
    }

    @Test
    void skipsUpdateWhenIconMissing() {
        when(faviconService.getFaviconUrl("https://example.com"))
                .thenReturn(CompletableFuture.completedFuture(""));

        listener.onUpdateFaviconEvent(UpdateFaviconEvent.builder()
                .fullShortUrl("localhost/abc")
                .gid("g1")
                .originUrl("https://example.com")
                .build());

        verify(shortLinkMapper, never()).update(any(), any());
    }

    @Test
    void ignoresBlankOrigin() {
        listener.onUpdateFaviconEvent(UpdateFaviconEvent.builder()
                .fullShortUrl("localhost/abc")
                .gid("g1")
                .originUrl(" ")
                .build());

        verify(faviconService, never()).getFaviconUrl(any());
        verify(shortLinkMapper, never()).update(any(), any());
    }
}
