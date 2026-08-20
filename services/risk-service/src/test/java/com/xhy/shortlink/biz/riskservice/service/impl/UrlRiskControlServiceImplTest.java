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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlRiskControlServiceImplTest {

    private UrlRiskControlServiceImpl riskControlService;

    @Mock
    private ObjectProvider<ChatClient> chatClientProvider;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec promptSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    /**
     * 本地回环地址会被 SSRF 守卫拒绝，fetch 抛非连接类 IOException，
     * 从而以 warning 摘要为入参走 AI 分支——不依赖外网即可覆盖 callAiForAnalysis。
     */
    private static final String SSRF_BLOCKED_URL = "http://127.0.0.1:8000/login";

    @BeforeEach
    void setUp() {
        lenient().when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        riskControlService = new UrlRiskControlServiceImpl(chatClientProvider);
        ReflectionTestUtils.setField(riskControlService, "jsoupTimeout", 3000);
    }

    private void stubAiResponse(ShortLinkRiskCheckRespDTO dto) {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(ShortLinkRiskCheckRespDTO.class)).thenReturn(dto);
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
    void checkUrlRisk_withoutChatClient_skipsModel() {
        when(chatClientProvider.getIfAvailable()).thenReturn(null);
        UrlRiskControlServiceImpl withoutAi = new UrlRiskControlServiceImpl(chatClientProvider);
        ReflectionTestUtils.setField(withoutAi, "jsoupTimeout", 3000);

        ShortLinkRiskCheckRespDTO result = withoutAi.checkUrlRisk("https://normal-site.com/page");

        assertNotNull(result);
        assertTrue(result.isSafe());
        assertEquals("NONE", result.getRiskType());
        assertTrue(result.getDetail().contains("未配置 AI 风控"));
    }

    @Test
    void checkUrlRisk_aiAnalysis_returnsModelVerdict() {
        stubAiResponse(ShortLinkRiskCheckRespDTO.builder()
                .safe(false).riskType("SCAM").summary("疑似诈骗").detail("诱导充值").build());

        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(SSRF_BLOCKED_URL);

        assertFalse(result.isSafe());
        assertEquals("SCAM", result.getRiskType());
        assertEquals("诱导充值", result.getDetail());
    }

    @Test
    void checkUrlRisk_aiDataInspectionFailed_returnsHighRisk() {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(ShortLinkRiskCheckRespDTO.class))
                .thenThrow(new RuntimeException("DataInspectionFailed: blocked by safety check"));

        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(SSRF_BLOCKED_URL);

        assertFalse(result.isSafe());
        assertEquals("HIGH_RISK", result.getRiskType());
        assertTrue(result.getDetail().contains("DataInspectionFailed"));
    }

    @Test
    void checkUrlRisk_aiServiceUnavailable_degradesToSafe() {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(ShortLinkRiskCheckRespDTO.class))
                .thenThrow(new RuntimeException("connection refused"));

        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(SSRF_BLOCKED_URL);

        assertTrue(result.isSafe());
        assertTrue(result.getDetail().contains("AI 服务暂时不可用"));
    }

    @Test
    void checkUrlRisk_unknownHost_returnsSuspicious() {
        // .invalid 是 RFC 2606 保留 TLD，解析必然失败 → UnknownHostException → SUSPICIOUS
        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk("https://doesnotexist.invalid/page");

        assertFalse(result.isSafe());
        assertEquals("SUSPICIOUS", result.getRiskType());
    }
}
