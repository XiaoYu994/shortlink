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
 * 空 api-key 仍会触发 DashScope 自动配置并在启动期断言失败。
 */
public class DashScopeOptionalEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE = "dashscope-optional";
    private static final String EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";
    private static final List<String> DASHSCOPE_AUTO_CONFIGS = List.of(
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAutoConfiguration",
            "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String apiKey = environment.getProperty("spring.ai.dashscope.api-key", "");
        if (StringUtils.hasText(apiKey)) {
            return;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.ai.model.chat", "none");
        List<String> excludes = new ArrayList<>();
        String existing = environment.getProperty(EXCLUDE_PROPERTY, "");
        if (StringUtils.hasText(existing)) {
            excludes.addAll(List.of(existing.split(",")));
        }
        for (String autoConfig : DASHSCOPE_AUTO_CONFIGS) {
            if (excludes.stream().noneMatch(item -> item.trim().equals(autoConfig))) {
                excludes.add(autoConfig);
            }
        }
        properties.put(EXCLUDE_PROPERTY, String.join(",", excludes));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, properties));
    }
}
