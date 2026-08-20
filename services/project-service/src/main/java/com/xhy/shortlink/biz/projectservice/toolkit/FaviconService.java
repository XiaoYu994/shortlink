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

package com.xhy.shortlink.biz.projectservice.toolkit;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

/**
 * 异步抓取目标网站 Favicon。
 */
@Slf4j
@Component
public class FaviconService {

    private static final int MAX_BODY_SIZE = 100 * 1024;
    private static final int HTML_TIMEOUT_MS = 3000;
    private static final int HEAD_TIMEOUT_MS = 2000;
    private static final int HTTP_OK = 200;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    /**
     * 从页面 &lt;link rel=icon&gt; 或站点根目录 /favicon.ico 解析图标地址。
     * 找不到时返回空字符串，由前端使用默认图。
     */
    @Async("crawlerExecutor")
    public CompletableFuture<String> getFaviconUrl(String targetUrl) {
        if (!StringUtils.hasText(targetUrl)) {
            return CompletableFuture.completedFuture("");
        }
        try {
            String htmlIcon = fetchIconFromHtml(targetUrl);
            if (StringUtils.hasText(htmlIcon)) {
                return CompletableFuture.completedFuture(htmlIcon);
            }
            String rootIcon = guessRootIcon(targetUrl);
            if (StringUtils.hasText(rootIcon) && isUrlAccessible(rootIcon)) {
                return CompletableFuture.completedFuture(rootIcon);
            }
        } catch (Exception e) {
            log.warn("获取图标异常: URL={}", targetUrl, e);
        }
        return CompletableFuture.completedFuture("");
    }

    private String fetchIconFromHtml(String targetUrl) {
        try {
            Document doc = Jsoup.connect(targetUrl)
                    .userAgent(USER_AGENT)
                    .timeout(HTML_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE)
                    .ignoreContentType(true)
                    .get();
            Elements links = doc.select(
                    "link[rel~=(?i)^(shortcut|icon|apple-touch-icon|apple-touch-icon-precomposed)$]");
            if (links.isEmpty()) {
                return null;
            }
            Element bestMatch = links.stream()
                    .max(Comparator.comparingInt(this::calculateIconScore))
                    .orElse(links.first());
            return bestMatch == null ? null : bestMatch.attr("abs:href");
        } catch (Exception e) {
            return null;
        }
    }

    String guessRootIcon(String targetUrl) {
        try {
            URI uri = URI.create(targetUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                return null;
            }
            int port = uri.getPort();
            if (port > 0) {
                return scheme + "://" + host + ":" + port + "/favicon.ico";
            }
            return scheme + "://" + host + "/favicon.ico";
        } catch (Exception e) {
            return null;
        }
    }

    int calculateIconScore(Element element) {
        String rel = element.attr("rel").toLowerCase();
        String sizes = element.attr("sizes");
        int score = 0;
        if (rel.contains("apple-touch-icon")) {
            score += 50;
        }
        if (StringUtils.hasText(sizes)) {
            score += 20;
            if (sizes.contains("192") || sizes.contains("144")) {
                score += 10;
            }
        }
        return score;
    }

    private boolean isUrlAccessible(String urlStr) {
        if (!StringUtils.hasText(urlStr)) {
            return false;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(HEAD_TIMEOUT_MS);
            connection.setReadTimeout(HEAD_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            return connection.getResponseCode() == HTTP_OK;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
