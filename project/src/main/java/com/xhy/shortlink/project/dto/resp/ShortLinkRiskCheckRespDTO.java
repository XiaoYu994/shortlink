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
