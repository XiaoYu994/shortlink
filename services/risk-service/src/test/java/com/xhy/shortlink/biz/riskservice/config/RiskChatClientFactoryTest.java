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
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RiskChatClientFactoryTest {

    @Test
    void missingConfig_returnsNull() {
        assertNull(RiskChatClientFactory.create(null));
        assertNull(RiskChatClientFactory.create(new RiskAiProperties()));
    }

    @Test
    void dashscope_buildsClient() {
        RiskAiProperties properties = new RiskAiProperties();
        properties.setProvider("dashscope");
        properties.setApiKey("sk-test");
        properties.setModel("qwen3-max");

        ChatClient client = RiskChatClientFactory.create(properties);
        assertNotNull(client);
    }

    @Test
    void openaiCompatible_buildsClient() {
        RiskAiProperties properties = new RiskAiProperties();
        properties.setProvider("openai");
        properties.setApiKey("sk-test");
        properties.setModel("deepseek-chat");
        properties.setBaseUrl("https://api.deepseek.com");

        ChatClient client = RiskChatClientFactory.create(properties);
        assertNotNull(client);
    }
}
