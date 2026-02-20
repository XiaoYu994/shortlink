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
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * URL 风控检测服务实现
 *
 * @author XiaoYu
 */
@Slf4j
@Service
public class UrlRiskControlServiceImpl implements UrlRiskControlService {

    private final ChatClient chatClient;

    @Value("${short-link.risk-control.jsoup-timeout:3000}")
    private int jsoupTimeout;

    private static final int MAX_ANALYSIS_CHARS = 2000;

    public UrlRiskControlServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        你是一个资深网络安全专家（Cybersecurity Analyst）。
                        你的核心任务是根据用户提供的【URL特征】和【网页内容摘要】，判断该链接是否存在安全风险。

                        如果发现风险，请严格按照以下分类进行归类 (riskType)：
                        1. PHISHING (网络钓鱼)：伪造银行、支付、社交账号登录页。
                        2. GAMBLING (非法赌博)：涉及真钱博彩、在线赌场、六合彩。
                        3. PORN (色情低俗)：包含露骨色情内容、招嫖信息。
                        4. SCAM (诈骗/杀猪盘)：虚假投资、刷单、中奖欺诈、贷款诈骗。
                        5. OTHER (其他违规)：政治敏感、暴力恐怖等。

                        请务必以 JSON 格式输出结果。
                        """)
                .build();
    }

    @Override
    public ShortLinkRiskCheckRespDTO checkUrlRisk(String url) {
        if (isWhiteList(url)) {
            return buildSafeResponse("白名单域名");
        }
        if (isBlackListPattern(url)) {
            return buildRiskResponse("PHISHING", "疑似钓鱼网址",
                    "命中本地黑名单关键词规则 (Suspicious Pattern)");
        }

        String pageContent;
        try {
            pageContent = fetchPageContent(url);
        } catch (Exception e) {
            if (isSuspiciousConnectionError(e)) {
                log.warn("网页访问异常，结合域名特征判黑。URL: {}, Error: {}",
                        url, e.getClass().getSimpleName());
                return buildRiskResponse("SUSPICIOUS", "网站无法访问",
                        "访问超时或域名不存在，疑似快闪钓鱼站");
            }
            pageContent = "[System Warning] Content fetch failed: " + e.getMessage();
        }

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

        return chatClient.prompt()
                .user(userPrompt)
                .call()
                .entity(ShortLinkRiskCheckRespDTO.class);
    }

    private String fetchPageContent(String url) throws Exception {
        Document doc = Jsoup.connect(url)
                .timeout(jsoupTimeout)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .followRedirects(true)
                .get();
        return String.format("Title: %s\nBody: %s", doc.title(), doc.body().text());
    }

    private ShortLinkRiskCheckRespDTO handleAiException(String url, Exception e) {
        if (e.getMessage() != null && e.getMessage().contains("DataInspectionFailed")) {
            log.warn("AI 平台内容安全风控拦截。URL: ", url);
            return buildRiskResponse("HIGH_RISK", "严重违规内容",
                    "AI 平台触发内容安全风控拦截 (DataInspectionFailed)");
        }
        log.error("AI 服务调用异常，执行降级放行策略。URL: {}", url, e);
        return ShortLinkRiskCheckRespDTO.builder()
                .safe(true).summary("系统审核中")
                .detail("AI 服务暂时不可用: " + e.getMessage()).build();
    }

    private boolean isWhiteList(String url) {
        return url.contains("aliyun.com") || url.contains("jd.com");
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
