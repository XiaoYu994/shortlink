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

package com.xhy.shortlink.biz.projectservice.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 过期短链接归档事件
 *
 * @author XiaoYu
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkExpireArchiveEvent {

    /**
     * 归档阶段
     */
    public enum Stage {
        /** 冻结 */
        FREEZE,

        /** 归档 */
        ARCHIVE
    }

    /** 幂等标识 */
    private String eventId;

    /** 分组标识 */
    private String gid;

    /** 完整短链接 */
    private String fullShortUrl;

    /** 过期时间 */
    private Date expireAt;

    /** 用户 ID */
    private Long userId;

    /** 归档阶段 */
    private Stage stage;
}
