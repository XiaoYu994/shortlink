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

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAiPropertiesTest {

    @Test
    void noneProvider_disabledEvenWithKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("short-link.risk-control.ai.provider", "none");
        environment.setProperty("short-link.risk-control.ai.api-key", "sk-test");

        assertFalse(RiskAiProperties.enabled(environment));
    }

    @Test
    void unsetProvider_withNewKey_enablesDashScope() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("short-link.risk-control.ai.api-key", "sk-from-env");

        RiskAiProperties resolved = RiskAiProperties.resolve(RiskAiProperties.bindFrom(environment), environment);
        assertTrue(resolved.isEnabled());
        assertEquals("dashscope", resolved.normalizedProvider());
        assertEquals("sk-from-env", resolved.getApiKey());
        assertEquals("qwen3-max", resolved.getModel());
    }

    @Test
    void dashscopeWithKey_enabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("short-link.risk-control.ai.provider", "dashscope");
        environment.setProperty("short-link.risk-control.ai.api-key", "sk-test");
        environment.setProperty("short-link.risk-control.ai.model", "qwen3-max");

        RiskAiProperties resolved = RiskAiProperties.resolve(RiskAiProperties.bindFrom(environment), environment);
        assertTrue(resolved.isEnabled());
        assertEquals("dashscope", resolved.normalizedProvider());
        assertEquals("qwen3-max", resolved.getModel());
    }

    @Test
    void openaiCompatible_readsBaseUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("short-link.risk-control.ai.provider", "openai");
        environment.setProperty("short-link.risk-control.ai.api-key", "sk-test");
        environment.setProperty("short-link.risk-control.ai.model", "deepseek-chat");
        environment.setProperty("short-link.risk-control.ai.base-url", "https://api.deepseek.com");

        RiskAiProperties resolved = RiskAiProperties.resolve(RiskAiProperties.bindFrom(environment), environment);
        assertTrue(resolved.isEnabled());
        assertEquals("openai", resolved.normalizedProvider());
        assertEquals("https://api.deepseek.com", resolved.getBaseUrl());
        assertEquals("deepseek-chat", resolved.getModel());
    }

    @Test
    void openaiBlankModel_defaultsGpt4oMini() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("short-link.risk-control.ai.provider", "openai");
        environment.setProperty("short-link.risk-control.ai.api-key", "sk-test");

        RiskAiProperties resolved = RiskAiProperties.resolve(RiskAiProperties.bindFrom(environment), environment);
        assertEquals("gpt-4o-mini", resolved.getModel());
    }

    @Test
    void openaiIgnoresLegacyDashScopeKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("short-link.risk-control.ai.provider", "openai");
        environment.setProperty("spring.ai.dashscope.api-key", "sk-legacy");

        RiskAiProperties resolved = RiskAiProperties.resolve(RiskAiProperties.bindFrom(environment), environment);
        assertFalse(resolved.isEnabled());
        assertEquals("", resolved.getApiKey());
    }

    @Test
    void legacyDashScopeKey_enablesWhenNewKeyMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.dashscope.api-key", "sk-legacy");
        environment.setProperty("spring.ai.dashscope.chat.options.model", "qwen-plus");

        RiskAiProperties resolved = RiskAiProperties.resolve(RiskAiProperties.bindFrom(environment), environment);
        assertTrue(resolved.isEnabled());
        assertEquals("dashscope", resolved.normalizedProvider());
        assertEquals("sk-legacy", resolved.getApiKey());
        assertEquals("qwen-plus", resolved.getModel());
    }

    @Test
    void blankKey_disabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("short-link.risk-control.ai.provider", "openai");

        assertFalse(RiskAiProperties.enabled(environment));
    }
}
