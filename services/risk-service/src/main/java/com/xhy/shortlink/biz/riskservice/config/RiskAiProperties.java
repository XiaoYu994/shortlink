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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * URL 风控大模型配置。支持 DashScope 与 OpenAI 兼容接口。
 */
@Data
@ConfigurationProperties(prefix = RiskAiProperties.PREFIX)
public class RiskAiProperties {

    public static final String PREFIX = "short-link.risk-control.ai";

    private static final String LEGACY_API_KEY = "spring.ai.dashscope.api-key";
    private static final String LEGACY_MODEL = "spring.ai.dashscope.chat.options.model";
    private static final String DEFAULT_DASHSCOPE_MODEL = "qwen3-max";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    public static final String PROVIDER_NONE = "none";
    public static final String PROVIDER_DASHSCOPE = "dashscope";
    public static final String PROVIDER_OPENAI = "openai";

    /**
     * 空：有 Key 时按 dashscope；none 关闭；dashscope / openai 为显式供应商。
     */
    private String provider = "";

    private String apiKey = "";

    private String model = "";

    /**
     * OpenAI 兼容根地址，例如 https://api.deepseek.com、https://api.x.ai。
     */
    private String baseUrl = "";

    /**
     * 结合 Nacos 新配置与历史 {@code spring.ai.dashscope.*} 得到实际生效值。
     */
    public static RiskAiProperties resolve(RiskAiProperties bound, Environment environment) {
        RiskAiProperties resolved = new RiskAiProperties();
        String configuredKey = bound == null ? "" : defaultString(bound.getApiKey());
        String configuredModel = bound == null ? "" : defaultString(bound.getModel());
        String provider = normalize(bound == null ? null : bound.getProvider());
        String legacyKey = environment.getProperty(LEGACY_API_KEY, "");
        String legacyModel = environment.getProperty(LEGACY_MODEL, "");

        String apiKey = configuredKey;
        if (!PROVIDER_OPENAI.equals(provider) && !PROVIDER_NONE.equals(provider)) {
            apiKey = firstText(configuredKey, legacyKey);
        }
        if (!StringUtils.hasText(provider) && StringUtils.hasText(apiKey)) {
            provider = PROVIDER_DASHSCOPE;
        }

        String model = configuredModel;
        if (PROVIDER_DASHSCOPE.equals(provider)) {
            model = firstText(configuredModel, legacyModel);
            if (!StringUtils.hasText(model)) {
                model = DEFAULT_DASHSCOPE_MODEL;
            }
        } else if (PROVIDER_OPENAI.equals(provider) && !StringUtils.hasText(model)) {
            model = DEFAULT_OPENAI_MODEL;
        }

        resolved.setProvider(provider);
        resolved.setApiKey(apiKey);
        resolved.setModel(model);
        resolved.setBaseUrl(bound == null ? "" : defaultString(bound.getBaseUrl()));
        return resolved;
    }

    public static boolean enabled(Environment environment) {
        return resolve(bindFrom(environment), environment).isEnabled();
    }

    public boolean isEnabled() {
        String normalized = normalize(provider);
        return StringUtils.hasText(apiKey)
                && (PROVIDER_DASHSCOPE.equals(normalized) || PROVIDER_OPENAI.equals(normalized));
    }

    public String normalizedProvider() {
        return normalize(provider);
    }

    static RiskAiProperties bindFrom(Environment environment) {
        RiskAiProperties properties = new RiskAiProperties();
        properties.setProvider(environment.getProperty(PREFIX + ".provider", ""));
        properties.setApiKey(environment.getProperty(PREFIX + ".api-key", ""));
        properties.setModel(environment.getProperty(PREFIX + ".model", ""));
        properties.setBaseUrl(environment.getProperty(PREFIX + ".base-url", ""));
        return properties;
    }

    /**
     * 归一化后的 provider 是否为受支持的值（none / dashscope / openai）。
     */
    public static boolean isKnownProvider(String normalizedProvider) {
        return PROVIDER_NONE.equals(normalizedProvider)
                || PROVIDER_DASHSCOPE.equals(normalizedProvider)
                || PROVIDER_OPENAI.equals(normalizedProvider);
    }

    static String normalize(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    static String firstText(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return "";
    }

    private static String defaultString(String value) {
        return value == null ? "" : value.trim();
    }
}
