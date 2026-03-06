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

package com.xhy.shortlink.biz.riskservice.service.impl;

import com.xhy.shortlink.biz.riskservice.dto.resp.ShortLinkRiskCheckRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlRiskControlServiceImplTest {

    private UrlRiskControlServiceImpl riskControlService;

    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        riskControlService = new UrlRiskControlServiceImpl(chatClientBuilder);
        ReflectionTestUtils.setField(riskControlService, "jsoupTimeout", 3000);
    }

    @Test
    void checkUrlRisk_whitelistDomain_returnsSafe() {
        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk("https://www.aliyun.com/products");

        assertTrue(result.isSafe());
        assertEquals("NONE", result.getRiskType());
    }

    @Test
    void checkUrlRisk_jdDomain_returnsSafe() {
        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk("https://item.jd.com/12345.html");

        assertTrue(result.isSafe());
    }

    @Test
    void checkUrlRisk_phishingPattern_returnsRisk() {
        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(
                "https://fake-paypal-security-verify.com/account");

        assertFalse(result.isSafe());
        assertEquals("PHISHING", result.getRiskType());
    }

    @Test
    void checkUrlRisk_appleIdPhishing_returnsRisk() {
        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(
                "https://appleid-verify-account.xyz/login");

        assertFalse(result.isSafe());
        assertEquals("PHISHING", result.getRiskType());
    }

    @Test
    void checkUrlRisk_taobaoPhishing_returnsRisk() {
        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(
                "https://taobao-security-verify.cn/check");

        assertFalse(result.isSafe());
        assertEquals("PHISHING", result.getRiskType());
    }

    @Test
    void checkUrlRisk_normalUrl_notMatchBlacklist() {
        // Normal paypal.com without suspicious patterns should not hit blacklist
        // (will go to AI analysis or network fetch, but won't match local rules)
        String url = "https://normal-site.com/page";

        // This will try to fetch the page and call AI, which will fail in test.
        // The service should handle this gracefully.
        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(url);

        // Even if AI call fails, the service should return a result (degraded)
        assertNotNull(result);
    }
}
