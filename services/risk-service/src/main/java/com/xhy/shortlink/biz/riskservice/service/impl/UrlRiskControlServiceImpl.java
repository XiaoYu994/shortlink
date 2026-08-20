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
import com.xhy.shortlink.biz.riskservice.service.UrlRiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * URL 风控检测服务实现
 *
 * @author XiaoYu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlRiskControlServiceImpl implements UrlRiskControlService {

    private final ObjectProvider<ChatClient> chatClientProvider;

    @Value("${short-link.risk-control.jsoup-timeout:3000}")
    private int jsoupTimeout;

    private static final int MAX_ANALYSIS_CHARS = 2000;
    private static final int MAX_REDIRECTS = 4;
    private static final int HTTP_REDIRECT_MIN_STATUS = 300;
    private static final int HTTP_REDIRECT_MAX_STATUS = 400;
    private static final int HTTP_ERROR_STATUS = 400;

    @Override
    public ShortLinkRiskCheckRespDTO checkUrlRisk(String url) {
        ShortLinkRiskCheckRespDTO result;
        if (isWhiteList(url)) {
            result = buildSafeResponse("白名单域名");
        } else if (isBlackListPattern(url)) {
            result = buildRiskResponse("PHISHING", "疑似钓鱼网址",
                    "命中本地黑名单关键词规则 (Suspicious Pattern)");
        } else if (chatClient() == null) {
            result = buildSafeResponse("未配置 AI 风控，跳过模型审核");
        } else {
            try {
                String pageContent = fetchPageContent(url);
                result = callAiSafely(url, pageContent);
            } catch (Exception e) {
                if (isSuspiciousConnectionError(e)) {
                    log.warn("网页访问异常，结合域名特征判黑。URL: {}, Error: {}",
                            url, e.getClass().getSimpleName());
                    result = buildRiskResponse("SUSPICIOUS", "网站无法访问",
                            "访问超时或域名不存在，疑似快闪钓鱼站");
                } else {
                    String warningContent = "[System Warning] Content fetch failed: " + e.getMessage();
                    result = callAiSafely(url, warningContent);
                }
            }
        }
        return result;
    }

    private ShortLinkRiskCheckRespDTO callAiSafely(String url, String pageContent) {
        try {
            return callAiForAnalysis(url, pageContent);
        } catch (Exception e) {
            return handleAiException(url, e);
        }
    }

    private ShortLinkRiskCheckRespDTO callAiForAnalysis(String url, String pageContent) {
        String userPrompt = """
                请分析以下目标信息：

                【目标 URL】: %s
                【网页文本摘要】:
                %s

                请按以下逻辑推理：
                1. 检查 URL 域名是否包含误导性关键词。
                2. 检查网页文本是否包含敏感词。
                3. 如果网页内容缺失，仅根据 URL 结构进行风险评估。

                请返回 JSON 格式，包含以下字段：
                1. "safe": boolean (是否安全)
                2. "riskType": string (从 PHISHING, GAMBLING, PORN, SCAM, OTHER, NONE 中选择)
                3. "summary": string (简短通知，中文，不超过10个字)
                4. "detail": string (详细的风控推理过程)
                """.formatted(url, StringUtils.truncate(pageContent, MAX_ANALYSIS_CHARS));

        return chatClient().prompt()
                .user(userPrompt)
                .call()
                .entity(ShortLinkRiskCheckRespDTO.class);
    }

    private ChatClient chatClient() {
        return chatClientProvider.getIfAvailable();
    }

    private String fetchPageContent(String url) throws Exception {
        String currentUrl = url;
        for (int redirectCount = 0; redirectCount < MAX_REDIRECTS; redirectCount++) {
            URI target = validateFetchTarget(currentUrl);
            var response = Jsoup.connect(target.toString())
                    .timeout(jsoupTimeout)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .followRedirects(false)
                    .execute();
            int statusCode = response.statusCode();
            if (statusCode >= HTTP_REDIRECT_MIN_STATUS && statusCode < HTTP_REDIRECT_MAX_STATUS) {
                String location = response.header("location");
                if (!StringUtils.hasText(location)) {
                    throw new IOException("重定向缺少目标地址");
                }
                currentUrl = target.resolve(location).toString();
                continue;
            }
            if (statusCode >= HTTP_ERROR_STATUS) {
                throw new IOException("网页响应状态码: " + statusCode);
            }
            Document doc = response.parse();
            return String.format("Title: %s\nBody: %s", doc.title(), doc.body().text());
        }
        throw new IOException("重定向次数过多");
    }

    private ShortLinkRiskCheckRespDTO handleAiException(String url, Exception e) {
        if (e.getMessage() != null && e.getMessage().contains("DataInspectionFailed")) {
            log.warn("AI 平台内容安全风控拦截。URL: {}", url);
            return buildRiskResponse("HIGH_RISK", "严重违规内容",
                    "AI 平台触发内容安全风控拦截 (DataInspectionFailed)");
        }
        log.error("AI 服务调用异常，执行降级放行策略。URL: {}", url, e);
        return ShortLinkRiskCheckRespDTO.builder()
                .safe(true).summary("系统审核中")
                .detail("AI 服务暂时不可用: " + e.getMessage()).build();
    }

    private boolean isWhiteList(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null && (isAllowedHost(host, "aliyun.com") || isAllowedHost(host, "jd.com"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isAllowedHost(String host, String domain) {
        String normalizedHost = host.toLowerCase();
        return normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain);
    }

    private URI validateFetchTarget(String url) throws IOException {
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("URL 格式无效", e);
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("仅支持 HTTP/HTTPS URL");
        }
        if (!StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null) {
            throw new IOException("URL 主机无效");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IOException("禁止访问本地网络地址");
            }
        }
        return uri;
    }

    private boolean isBlackListPattern(String url) {
        String lowerUrl = url.toLowerCase();
        return (lowerUrl.contains("paypal") || lowerUrl.contains("appleid")
                || lowerUrl.contains("taobao"))
                && (lowerUrl.contains("security") || lowerUrl.contains("verify")
                || lowerUrl.contains("account"));
    }

    private boolean isSuspiciousConnectionError(Exception e) {
        return e instanceof UnknownHostException || e instanceof SocketTimeoutException;
    }

    private ShortLinkRiskCheckRespDTO buildSafeResponse(String desc) {
        return ShortLinkRiskCheckRespDTO.builder()
                .safe(true).riskType("NONE").summary("正常").detail(desc).build();
    }

    private ShortLinkRiskCheckRespDTO buildRiskResponse(String type, String summary,
                                                         String detail) {
        return ShortLinkRiskCheckRespDTO.builder()
                .safe(false).riskType(type).summary(summary).detail(detail).build();
    }
}
