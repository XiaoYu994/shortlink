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

package com.xhy.shortlink.project.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/*
* 查询短链接访客类型请求参数
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkUvTypeReqDTO {

    /*
    * 完整短链接
    * */
    private String fullShortUrl;
    /*
    * 分组标识
    * */
    private String gid;
    /*
    * 短链接启用标识
    * */
    private Integer enableStatus;
    /*
    * 开始时间
    * */
    private String startDate;
    /*
    * 结束时间
    * */
    private String endDate;
    /*
    * 用户访问集合
    * */
    private List<String> userAccessLogsList;

}
