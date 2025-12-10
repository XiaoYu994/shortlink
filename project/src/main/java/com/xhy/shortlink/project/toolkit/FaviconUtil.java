package com.xhy.shortlink.project.toolkit;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Comparator;

/*
* 获取目标网站的 Favicon 图标链接
* */
public class FaviconUtil {
    // 限制下载 HTML 的大小为 100KB，只读头部足以获取 Icon，极大提升速度
    private static final int MAX_BODY_SIZE = 100 * 1024;
    // 默认兜底图标
    private static final String DEFAULT_ICON = "https://web-cangqio.oss-cn-hangzhou.aliyuncs.com/c03071dcdc52c24e0aab256518e51557.png%7Etplv-gjr78lqtd0-image.image";

    public static String getFaviconUrl(String targetUrl) {
        String faviconUrl = null;
        try {
            // 1. 尝试从 HTML 解析
            faviconUrl = fetchIconFromHtml(targetUrl);

            // 2. 如果 HTML 中未找到，尝试根目录猜测 (例如 https://site.com/favicon.ico)
            if (StringUtils.isBlank(faviconUrl)) {
                faviconUrl = guessRootIcon(targetUrl);
            }

            // 3. 最终验证：如果找到的 URL 无法访问（404），则回退
            if (!isUrlAccessible(faviconUrl)) {
                return DEFAULT_ICON;
            }

            return faviconUrl;

        } catch (Exception e) {
            // 记录日志...
            return DEFAULT_ICON;
        }
    }

    /**
     * 策略 A: 从 HTML Head 中解析，优先获取高清图
     */
    private static String fetchIconFromHtml(String targetUrl) {
        try {
            Document doc = Jsoup.connect(targetUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(3000)
                    .maxBodySize(MAX_BODY_SIZE) // 核心优化：只读前 100KB
                    .ignoreContentType(true)    // 防止某些网站返回非 text/html 类型报错
                    .get();

            // 获取所有可能的 icon 标签
            Elements links = doc.select("link[rel~=(?i)^(shortcut|icon|apple-touch-icon|apple-touch-icon-precomposed)$]");

            if (links.isEmpty()) {
                return null;
            }

            // 排序逻辑：优先找 apple-touch-icon (通常更高清)，其次找包含 sizes 属性的
            Element bestMatch = links.stream()
                    .max(Comparator.comparingInt(FaviconUtil::calculateIconScore))
                    .orElse(links.first());

            return bestMatch.attr("abs:href");

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 给图标打分，用于排序
     * 规则：apple-touch-icon > sizes="xxx" > 普通 icon
     */
    private static int calculateIconScore(Element element) {
        String rel = element.attr("rel").toLowerCase();
        String sizes = element.attr("sizes");

        int score = 0;
        if (rel.contains("apple-touch-icon")) {
            score += 50; // 最高优先级
        }
        if (StringUtils.isNotBlank(sizes)) {
            score += 20; // 有尺寸定义的通常比默认的高清
            if (sizes.contains("192") || sizes.contains("144")) {
                score += 10; // 越大越好
            }
        }
        return score;
    }

    /**
     * 策略 B: 猜测根目录 /favicon.ico
     */
    private static String guessRootIcon(String targetUrl) {
        try {
            URL url = new URL(targetUrl);
            return url.getProtocol() + "://" + url.getHost() + "/favicon.ico";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 辅助方法：检查 URL 是否可访问 (使用 HEAD 请求，开销极小)
     */
    private static boolean isUrlAccessible(String urlStr) {
        if (StringUtils.isBlank(urlStr)) return false;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD"); // 关键：只请求头信息，不下载图片内容
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
