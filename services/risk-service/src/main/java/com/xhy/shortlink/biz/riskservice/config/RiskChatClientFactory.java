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

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.StringUtils;

/**
 * 按供应商手工构造 ChatClient，避开 Spring AI 自动配置对空 Key 的启动断言。
 */
public final class RiskChatClientFactory {

    static final String SYSTEM_PROMPT = """
            你是一个资深网络安全专家（Cybersecurity Analyst）。
            你的核心任务是根据用户提供的【URL特征】和【网页内容摘要】，判断该链接是否存在安全风险。

            如果发现风险，请严格按照以下分类进行归类 (riskType)：
            1. PHISHING (网络钓鱼)：伪造银行、支付、社交账号登录页。
            2. GAMBLING (非法赌博)：涉及真钱博彩、在线赌场、六合彩。
            3. PORN (色情低俗)：包含露骨色情内容、招嫖信息。
            4. SCAM (诈骗/杀猪盘)：虚假投资、刷单、中奖欺诈、贷款诈骗。
            5. OTHER (其他违规)：政治敏感、暴力恐怖等。

            请务必以 JSON 格式输出结果。
            """;

    private RiskChatClientFactory() {
    }

    public static ChatClient create(RiskAiProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            return null;
        }
        ChatModel chatModel = switch (properties.normalizedProvider()) {
            case "dashscope" -> dashScopeModel(properties);
            case "openai" -> openAiModel(properties);
            default -> null;
        };
        if (chatModel == null) {
            return null;
        }
        return ChatClient.builder(chatModel).defaultSystem(SYSTEM_PROMPT).build();
    }

    private static ChatModel dashScopeModel(RiskAiProperties properties) {
        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(properties.getApiKey());
        if (StringUtils.hasText(properties.getBaseUrl())) {
            apiBuilder.baseUrl(properties.getBaseUrl());
        }
        return DashScopeChatModel.builder()
                .dashScopeApi(apiBuilder.build())
                .defaultOptions(DashScopeChatOptions.builder().model(properties.getModel()).build())
                .build();
    }

    private static ChatModel openAiModel(RiskAiProperties properties) {
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(properties.getApiKey());
        if (StringUtils.hasText(properties.getBaseUrl())) {
            apiBuilder.baseUrl(properties.getBaseUrl());
        }
        return OpenAiChatModel.builder()
                .openAiApi(apiBuilder.build())
                .defaultOptions(OpenAiChatOptions.builder().model(properties.getModel()).build())
                .build();
    }
}
