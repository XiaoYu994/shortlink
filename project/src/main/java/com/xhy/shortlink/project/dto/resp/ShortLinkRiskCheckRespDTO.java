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
    private boolean safe;
    private String riskType; // e.g., "PHISHING", "GAMBLING", "PORN", "NONE"
    private String description;
}
