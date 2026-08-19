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

package com.xhy.shortlink.biz.userservice.config;

import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowLimitInterceptorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    @Test
    void preHandle_disabled_allowsRequest() {
        FlowLimitProperties properties = new FlowLimitProperties();
        properties.setEnable(false);
        FlowLimitInterceptor interceptor = new FlowLimitInterceptor(stringRedisTemplate, properties);

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void preHandle_overLimit_throwsClientException() {
        FlowLimitProperties properties = new FlowLimitProperties();
        properties.setEnable(true);
        properties.setTimeWindow(1);
        properties.setMaxAccessCount(2);
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(3L);
        when(request.getRequestURI()).thenReturn("/api/short-link/admin/v1/user/login");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        FlowLimitInterceptor interceptor = new FlowLimitInterceptor(stringRedisTemplate, properties);

        assertThrows(ClientException.class, () -> interceptor.preHandle(request, response, new Object()));
    }
}
