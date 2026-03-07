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

package com.xhy.shortlink.biz.projectservice.service.impl;

import com.xhy.shortlink.biz.projectservice.service.UrlTitleService;
import com.xhy.shortlink.framework.starter.convention.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;

/** 获取网站标题实现层 */
@Slf4j
@Service
public class UrlTitleServiceImpl implements UrlTitleService {

    private static final int JSOUP_TIMEOUT_MS = 5000;
    private static final int HTTP_OK = 200;

    @Override
    public String getPageTitle(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .timeout(JSOUP_TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .ignoreHttpErrors(true)
                    .execute();
            if (response.statusCode() != HTTP_OK) {
                log.warn("获取标题失败，状态码: {}, URL: {}", response.statusCode(), url);
                throw new ServiceException("无法访问该网站");
            }
            Document doc = response.parse();
            return doc.title();
        } catch (SocketTimeoutException e) {
            log.warn("获取标题超时: {}", url);
            throw new ServiceException("访问超时");
        } catch (IOException e) {
            log.error("获取标题发生网络异常: {}, URL: {}", e.getMessage(), url);
            throw new ServiceException("无法获取标题");
        }
    }
}
