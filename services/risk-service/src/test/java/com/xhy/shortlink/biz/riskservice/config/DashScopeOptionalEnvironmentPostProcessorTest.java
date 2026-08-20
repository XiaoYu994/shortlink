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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashScopeOptionalEnvironmentPostProcessorTest {

    private final DashScopeOptionalEnvironmentPostProcessor processor =
            new DashScopeOptionalEnvironmentPostProcessor();

    @Test
    void blankKey_disablesChatModelAndExcludesDashScope() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.dashscope.api-key", "");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("none", environment.getProperty("spring.ai.model.chat"));
        String excludes = environment.getProperty("spring.autoconfigure.exclude");
        assertNotNull(excludes);
        assertTrue(excludes.contains("com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAutoConfiguration"));
        assertTrue(excludes.contains("com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration"));
    }

    @Test
    void presentKey_leavesAutoConfigUntouched() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.dashscope.api-key", "sk-test");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertNull(environment.getProperty("spring.ai.model.chat"));
        assertNull(environment.getProperty("spring.autoconfigure.exclude"));
    }
}
