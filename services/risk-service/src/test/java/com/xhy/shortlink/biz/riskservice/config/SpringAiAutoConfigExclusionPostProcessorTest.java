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
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiAutoConfigExclusionPostProcessorTest {

    private static final String DASHSCOPE_CHAT_AUTOCONFIG =
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration";
    private static final String CHAT_CLIENT_AUTOCONFIG =
            "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration";

    private final SpringAiAutoConfigExclusionPostProcessor processor =
            new SpringAiAutoConfigExclusionPostProcessor();

    @Test
    void alwaysExcludesSpringAiAutoConfigEvenWhenLegacyKeyPresent() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.dashscope.api-key", "sk-test");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("none", environment.getProperty("spring.ai.model.chat"));
        String excludes = environment.getProperty("spring.autoconfigure.exclude");
        assertNotNull(excludes);
        assertTrue(excludes.contains(DASHSCOPE_CHAT_AUTOCONFIG));
        assertTrue(excludes.contains(CHAT_CLIENT_AUTOCONFIG));
    }

    @Test
    void appendsToYamlListExcludes() {
        // YAML 列表在真实环境被扁平化为索引键，整串属性读不到
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.autoconfigure.exclude[0]", "com.example.FooAutoConfiguration");
        environment.setProperty("spring.autoconfigure.exclude[1]", "com.example.BarAutoConfiguration");

        processor.postProcessEnvironment(environment, new SpringApplication());

        List<String> excludes = split(environment.getProperty("spring.autoconfigure.exclude"));
        assertTrue(excludes.contains("com.example.FooAutoConfiguration"));
        assertTrue(excludes.contains("com.example.BarAutoConfiguration"));
        assertTrue(excludes.contains(DASHSCOPE_CHAT_AUTOCONFIG));
    }

    @Test
    void appendsToCommaSeparatedExcludes() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.autoconfigure.exclude", "com.example.FooAutoConfiguration");

        processor.postProcessEnvironment(environment, new SpringApplication());

        List<String> excludes = split(environment.getProperty("spring.autoconfigure.exclude"));
        assertTrue(excludes.contains("com.example.FooAutoConfiguration"));
        assertTrue(excludes.contains(DASHSCOPE_CHAT_AUTOCONFIG));
    }

    @Test
    void doesNotDuplicateExistingExcludes() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.autoconfigure.exclude[0]", DASHSCOPE_CHAT_AUTOCONFIG);

        processor.postProcessEnvironment(environment, new SpringApplication());

        List<String> excludes = split(environment.getProperty("spring.autoconfigure.exclude"));
        assertEquals(1, excludes.stream().filter(DASHSCOPE_CHAT_AUTOCONFIG::equals).count());
    }

    @Test
    void excludesListDoesNotContainNonexistentDashScopeAutoConfiguration() {
        // 1.1.0.0-RC2 的 AutoConfiguration.imports 中不存在 DashScopeAutoConfiguration
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        List<String> excludes = split(environment.getProperty("spring.autoconfigure.exclude"));
        assertTrue(excludes.stream().noneMatch(
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAutoConfiguration"::equals));
    }

    private List<String> split(String value) {
        assertNotNull(value);
        return Arrays.stream(value.split(",")).map(String::trim).toList();
    }
}