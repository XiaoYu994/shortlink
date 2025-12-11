package com.xhy.shortlink.project.toolkit;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

/**
 * 网站图标获取服务 (异步版)
 */
@Slf4j
@Component
public class FaviconService {

    private static final int MAX_BODY_SIZE = 100 * 1024;
    private static final String DEFAULT_ICON = "https://web-cangqio.oss-cn-hangzhou.aliyuncs.com/c03071dcdc52c24e0aab256518e51557.png%7Etplv-gjr78lqtd0-image.image";

    /**
     * 异步获取目标网站的 Favicon 图标链接
     * @param targetUrl 目标网站地址
     * @return 包含图标 URL 的 Future 对象
     */
    @Async("crawlerExecutor") // 指定自定义线程池，防止占用主线程资源
    public CompletableFuture<String> getFaviconUrl(String targetUrl) {
        String faviconUrl = DEFAULT_ICON;
        try {
            long start = System.currentTimeMillis();

            // 1. 尝试从 HTML 解析
            String htmlIcon = fetchIconFromHtml(targetUrl);

            if (StringUtils.isNotBlank(htmlIcon)) {
                faviconUrl = htmlIcon;
            } else {
                // 2. 如果 HTML 中未找到，尝试根目录猜测
                String rootIcon = guessRootIcon(targetUrl);
                if (StringUtils.isNotBlank(rootIcon) && isUrlAccessible(rootIcon)) {
                    faviconUrl = rootIcon;
                }
            }

            log.debug("图标爬取完成 [{}], 耗时: {}ms", targetUrl, System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("获取图标异常: URL={}", targetUrl, e);
            // 异常时返回默认图标
            return CompletableFuture.completedFuture(DEFAULT_ICON);
        }

        // 将结果包装为 Future 返回
        return CompletableFuture.completedFuture(faviconUrl);
    }

    /**
     * 策略 A: 从 HTML Head 中解析
     */
    private String fetchIconFromHtml(String targetUrl) {
        try {
            Document doc = Jsoup.connect(targetUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(3000)
                    .maxBodySize(MAX_BODY_SIZE)
                    .ignoreContentType(true)
                    .get();

            Elements links = doc.select("link[rel~=(?i)^(shortcut|icon|apple-touch-icon|apple-touch-icon-precomposed)$]");

            if (links.isEmpty()) {
                return null;
            }

            Element bestMatch = links.stream()
                    .max(Comparator.comparingInt(this::calculateIconScore))
                    .orElse(links.first());

            return bestMatch.attr("abs:href");

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 策略 B: 猜测根目录
     */
    private String guessRootIcon(String targetUrl) {
        try {
            URL url = new URL(targetUrl);
            return url.getProtocol() + "://" + url.getHost() + "/favicon.ico";
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateIconScore(Element element) {
        String rel = element.attr("rel").toLowerCase();
        String sizes = element.attr("sizes");

        int score = 0;
        if (rel.contains("apple-touch-icon")) {
            score += 50;
        }
        if (StringUtils.isNotBlank(sizes)) {
            score += 20;
            if (sizes.contains("192") || sizes.contains("144")) {
                score += 10;
            }
        }
        return score;
    }

    private boolean isUrlAccessible(String urlStr) {
        if (StringUtils.isBlank(urlStr)) return false;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}