package com.xhy.shortlink.project.service.Impl;

import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.service.UrlTitleService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;

/*
* 获取网站标题实现层
* */
@Service
public class UrlTitleServiceImpl implements UrlTitleService {
    @Override
    public String getPageTitle(String url) {
        try {
            // 1. 发起请求并解析 HTML
            Document doc = Jsoup.connect(url)
                    // 设置超时时间（毫秒），避免目标网站卡死导致你的接口超时
                    .timeout(5000)
                    // 模拟浏览器 User-Agent，防止被反爬虫拦截
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();
            // 2. 直接获取 Title
            return doc.title();
        } catch (IOException e) {
            // 处理异常：如网络不通、URL错误等
            throw new ServiceException("获取标题失败: " + e.getMessage());
        }
    }
}
