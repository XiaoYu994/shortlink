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

package com.xhy.shortlink.project.service.Impl;

import com.xhy.shortlink.project.dto.resp.ShortLinkRiskCheckRespDTO;
import com.xhy.shortlink.project.service.UrlRiskControlService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

@Slf4j
@Service
public class UrlRiskControlServiceImpl implements UrlRiskControlService {

    private final ChatClient chatClient;

    // 配置抓取超时时间 (毫秒)
    @Value("${short-link.risk-control.jsoup-timeout:3000}")
    private int jsoupTimeout;

    // 最大分析字符数 (防止 Token 爆炸)
    private static final int MAX_ANALYSIS_CHARS = 2000;

    public UrlRiskControlServiceImpl(ChatClient.Builder chatClientBuilder) {
        // 构建单例 ChatClient，预设系统人设
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
        // 1. 【前置规则】本地黑名单/白名单快速过滤 (省钱 + 提速)
        if (isWhiteList(url)) {
            return buildSafeResponse("白名单域名");
        }
        if (isBlackListPattern(url)) {
            return buildRiskResponse("PHISHING", "疑似钓鱼网址", "命中本地黑名单关键词规则 (Suspicious Pattern)");
        }

        // 2. 【数据获取】抓取网页内容 (作为 AI 的“眼睛”)
        String pageContent;
        try {
            pageContent = fetchPageContent(url);
        } catch (Exception e) {
            // 如果抓取失败，分析失败原因
            // 某些钓鱼网站存活时间极短，无法访问往往意味着风险
            if (isSuspiciousConnectionError(e)) {
                log.warn("网页访问异常，结合域名特征判黑。URL: {}, Error: {}", url, e.getClass().getSimpleName());
                return buildRiskResponse("SUSPICIOUS", "网站无法访问", "访问超时或域名不存在，疑似快闪钓鱼站");
            }
            // 普通超时，标记为无法获取内容，仅让 AI 分析 URL 本身
            pageContent = "[System Warning] Content fetch failed: " + e.getMessage();
        }

        // 3. 【AI 判决】调用大模型进行深度语义分析
        try {
            return callAiForAnalysis(url, pageContent);
        } catch (Exception e) {
            // 4. 【异常兜底】处理 AI 调用过程中的异常
            return handleAiException(url, e);
        }
    }

    /**
     * 调用 AI 模型
     */
    private ShortLinkRiskCheckRespDTO callAiForAnalysis(String url, String pageContent) {
        // 构造包含思维链 (CoT) 的提示词
        String userPrompt = """
                请分析以下目标信息：
                
                【目标 URL】: %s
                【网页文本摘要】: 
                %s
                
                请按以下逻辑推理：
                1. 检查 URL 域名是否包含误导性关键词（如将 'taobao' 拼写为 'taobca'，或包含 'vip', 'secure' 等）。
                2. 检查网页文本是否包含敏感词（如“充值”、“提现”、“性感荷官”、“账号解冻”）。
                3. 如果网页内容缺失（显示 System Warning），仅根据 URL 结构进行风险评估。
                
                请返回 JSON 格式，包含以下字段：
                1. "safe": boolean (是否安全)
                2. "riskType": string (从 PHISHING, GAMBLING, PORN, SCAM, OTHER, NONE 中选择)
                3. "summary": string (给用户看的简短通知，必须中文，不超过10个字。例如："涉及网络赌博"、"疑似诈骗网站")
                4. "detail": string (详细的风控推理过程，说明判断依据)
                """.formatted(url, StringUtils.truncate(pageContent, MAX_ANALYSIS_CHARS));

        // 使用 .entity() 自动映射回 Java 对象
        return chatClient.prompt()
                .user(userPrompt)
                .call()
                .entity(ShortLinkRiskCheckRespDTO.class);
    }

    /**
     * 辅助方法：使用 Jsoup 抓取网页纯文本
     */
    private String fetchPageContent(String url) throws Exception {
        Document doc = Jsoup.connect(url)
                .timeout(jsoupTimeout)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .referrer("https://www.google.com/")
                .followRedirects(true)
                .get();

        String title = doc.title();
        String body = doc.body().text();
        return String.format("Title: %s\nBody: %s", title, body);
    }

    /**
     * 异常处理中心
     */
    private ShortLinkRiskCheckRespDTO handleAiException(String url, Exception e) {
        // 捕获阿里云 DashScope 的内容安全拦截异常
        // 当错误信息包含 "DataInspectionFailed" 时，说明内容极度违规（色情/涉政），AI 拒绝生成
        if (e.getMessage() != null && e.getMessage().contains("DataInspectionFailed")) {
            log.warn("AI 平台内容安全风控拦截 (确认为高危链接)。URL: {}", url);
            return buildRiskResponse("HIGH_RISK", "严重违规内容", "AI 平台触发内容安全风控拦截 (DataInspectionFailed)");
        }

        // 其他异常（如网络超时、Token不足），执行 Fail-Open（默认放行）策略，避免阻塞业务
        log.error("AI 服务调用异常，执行降级放行策略。URL: {}, Error: {}", url, e.getMessage());
        return ShortLinkRiskCheckRespDTO.builder()
                .safe(true) // 降级放行
                .summary("系统审核中")
                .detail("AI 服务暂时不可用: " + e.getMessage())
                .build();
    }

    // --- 辅助规则判断 ---

    private boolean isWhiteList(String url) {
        // 简单示例，实际应查数据库或 Redis 缓存
        return url.contains("aliyun.com") || url.contains("jd.com");
    }

    private boolean isBlackListPattern(String url) {
        String lowerUrl = url.toLowerCase();
        // 典型钓鱼特征：大厂名 + 敏感动词
        return (lowerUrl.contains("paypal") || lowerUrl.contains("appleid") || lowerUrl.contains("taobao")) &&
                (lowerUrl.contains("security") || lowerUrl.contains("verify") || lowerUrl.contains("account"));
    }

    private boolean isSuspiciousConnectionError(Exception e) {
        // 如果是“未知主机”或“超时”，且不是本地网络问题，可能是域名刚被封禁
        return e instanceof UnknownHostException || e instanceof SocketTimeoutException;
    }

    private ShortLinkRiskCheckRespDTO buildSafeResponse(String desc) {
        return ShortLinkRiskCheckRespDTO.builder()
                .safe(true)
                .riskType("NONE")
                .summary("正常")
                .detail(desc)
                .build();
    }

    private ShortLinkRiskCheckRespDTO buildRiskResponse(String type, String summary, String detail) {
        return ShortLinkRiskCheckRespDTO.builder()
                .safe(false)
                .riskType(type)
                .summary(summary) // 给用户看
                .detail(detail)   // 给管理员看
                .build();
    }
}