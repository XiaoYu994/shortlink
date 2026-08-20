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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 始终关闭 Spring AI（含 DashScope）自动配置。ChatClient 由 {@link RiskChatClientConfiguration} 按 Nacos 手工创建，
 * 避免空 Key 或 Nacos 晚于自动配置加载时启动失败。
 * <p>
 * 会与配置中已有的 {@code spring.autoconfigure.exclude} 合并（覆盖 YAML 列表的索引键形式与逗号分隔形式），
 * 不会抹掉运维手工追加的排除项。
 */
public class SpringAiAutoConfigExclusionPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE = "spring-ai-auto-config-off";
    private static final String EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";
    private static final List<String> AI_AUTO_CONFIGS = List.of(
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeVideoAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration",
            "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.ai.model.chat", "none");
        List<String> excludes = collectExistingExcludes(environment);
        for (String autoConfig : AI_AUTO_CONFIGS) {
            if (excludes.stream().noneMatch(item -> item.equals(autoConfig))) {
                excludes.add(autoConfig);
            }
        }
        properties.put(EXCLUDE_PROPERTY, String.join(",", excludes));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, properties));
    }

    /**
     * 收集配置中已有的排除项。YAML 列表会被扁平化为 {@code spring.autoconfigure.exclude[0..n]} 索引键，
     * 整串读取返回 null，因此需要两种形式都尝试。
     */
    private List<String> collectExistingExcludes(ConfigurableEnvironment environment) {
        List<String> excludes = new ArrayList<>();
        String flat = environment.getProperty(EXCLUDE_PROPERTY, "");
        if (StringUtils.hasText(flat)) {
            collect(flat, excludes);
        }
        for (int i = 0; ; i++) {
            String indexed = environment.getProperty(EXCLUDE_PROPERTY + "[" + i + "]");
            if (indexed == null) {
                break;
            }
            collect(indexed, excludes);
        }
        return new ArrayList<>(excludes.stream().distinct().toList());
    }

    private void collect(String value, List<String> excludes) {
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                excludes.add(item.trim());
            }
        }
    }
}