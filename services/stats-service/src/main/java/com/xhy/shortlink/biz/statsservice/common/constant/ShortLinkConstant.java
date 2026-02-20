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

package com.xhy.shortlink.biz.statsservice.common.constant;

/**
 * 短链接业务常量（stats-service 使用）
 */
public final class ShortLinkConstant {

    private ShortLinkConstant() {
    }

    /** Redis ZSet 数据过期时间（小时） */
    public static final long TODAY_EXPIRETIME = 48;

    /** 高德地图 IP 定位 API */
    public static final String AMAP_REMOTE_URL = "https://restapi.amap.com/v3/ip";
}
