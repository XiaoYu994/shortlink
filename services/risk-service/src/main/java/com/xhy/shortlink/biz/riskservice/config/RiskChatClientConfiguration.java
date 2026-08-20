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

package com.xhy.shortlink.biz.riskservice.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 从 Nacos / 本地配置手工装配风控 ChatClient。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RiskAiProperties.class)
public class RiskChatClientConfiguration {

    private final RiskAiProperties properties;
    private final Environment environment;

    /**
     * 启动时打印是否启用模型审核。改 Key 或供应商后需要重启进程。
     */
    @PostConstruct
    public void logStatus() {
        RiskAiProperties resolved = RiskAiProperties.resolve(properties, environment);
        if (resolved.isEnabled()) {
            log.info("URL 风控 AI 已启用, provider={}, model={}", resolved.normalizedProvider(), resolved.getModel());
            return;
        }
        String provider = resolved.normalizedProvider();
        if (!StringUtils.hasText(provider)) {
            log.info("URL 风控 AI 未启用，仅使用本地黑白名单");
        } else if (RiskAiProperties.PROVIDER_NONE.equals(provider)) {
            log.info("URL 风控 AI 已配置为关闭 (provider=none)");
        } else if (RiskAiProperties.isKnownProvider(provider)) {
            log.warn("URL 风控 AI 未启用: provider={} 但缺少 api-key，请检查 RISK_AI_API_KEY/DASHSCOPE_API_KEY", provider);
        } else {
            log.warn("URL 风控 AI 未启用: provider='{}' 无效，支持值: dashscope / openai / none", provider);
        }
    }

    @Bean
    @Conditional(RiskAiEnabledCondition.class)
    public ChatClient riskChatClient() {
        return RiskChatClientFactory.create(RiskAiProperties.resolve(properties, environment));
    }
}
