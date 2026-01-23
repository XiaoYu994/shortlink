package com.xhy.shortlink.project.service;

import com.xhy.shortlink.project.dto.resp.ShortLinkRiskCheckRespDTO;

public interface UrlRiskControlService {
    /**
     * Check if the URL is safe.
     * @param url The URL to check
     * @return Risk check result
     */
    ShortLinkRiskCheckRespDTO checkUrlRisk(String url);
}
