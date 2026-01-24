package com.xhy.shortlink.project.service.Impl;

import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.service.UrlTitleService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;

/*
* 获取网站标题实现层
* */
@Slf4j
@Service
public class UrlTitleServiceImpl implements UrlTitleService {
    @Override
    public String getPageTitle(String url) {
        try {
            // 1. 发起请求并解析 HTML
            Connection.Response response = Jsoup.connect(url)
                    // 设置超时时间（毫秒），避免目标网站卡死导致你的接口超时
                    .timeout(5000)
                    // 模拟浏览器 User-Agent，防止被反爬虫拦截
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .ignoreHttpErrors(true)
                    .execute();
            // 2. 检查状态码
            if (response.statusCode() != 200) {
                // 如果是 404 或其他错误，返回一个默认提示，而不是报错
                log.warn("获取标题失败，状态码: {}, URL: {}", response.statusCode(), url);
                throw new ServiceException("无法访问该网站");
            }

            // 3. 解析 Title
            Document doc = response.parse();
            return doc.title();

        } catch (SocketTimeoutException e) {
            // 超时特殊处理
            log.warn("获取标题超时: {}", url);
            throw new ServiceException("访问超时");
        } catch (IOException e) {
            // 其他网络错误（如域名解析失败、SSL握手失败）
            log.error("获取标题发生网络异常: {}, URL: {}", e.getMessage(), url);
            throw new ServiceException("无法获取标题");
        }
    }
}
