package com.xhy.shortlink.project.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkRiskCheckRespDTO {
    /**
     * 是否安全 (true=安全, false=有风险)
     */
    private boolean safe;

    /**
     * 风险类型 (存数据库/枚举):
     * [PHISHING, GAMBLING, PORN, SCAM, OTHER]
     */
    private String riskType;

    /**
     * 简短描述 (给用户发通知用):
     * 例如："涉及网络赌博"、"疑似诈骗网站"
     * 限制在 10-15 字以内
     */
    private String summary;

    /**
     * 详细推理 (风控日志用):
     * 例如："域名使用了拼写混淆技术..."
     */
    private String detail;
}
