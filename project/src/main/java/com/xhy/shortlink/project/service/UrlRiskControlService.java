package com.xhy.shortlink.project.service;

import com.xhy.shortlink.project.dto.resp.ShortLinkRiskCheckRespDTO;

public interface UrlRiskControlService {
    /**
     * 检测 URL 是否合法
     * @param url 创建的原始链接
     * @return 检测的结果
     */
    ShortLinkRiskCheckRespDTO checkUrlRisk(String url);
}
