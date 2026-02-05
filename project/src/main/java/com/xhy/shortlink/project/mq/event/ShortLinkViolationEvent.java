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

package com.xhy.shortlink.project.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/*
*  发送风控通知事件
* */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkViolationEvent {
    private String fullShortUrl; // 违规链接
    private String gid;          // 归属组 (用于查找用户)
    private String reason;       // 违规原因 (色情/赌博等)
    private LocalDateTime time;  // 违规时间
    private Long userId; // 用户 id
    private String eventId; // 消息幂等 id
}
