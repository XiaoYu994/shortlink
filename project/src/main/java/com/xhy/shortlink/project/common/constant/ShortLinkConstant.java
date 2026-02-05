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

package com.xhy.shortlink.project.common.constant;

public class ShortLinkConstant {

    /*
    * 默认缓存有效期 1天
    * */
    public static long DEFAULT_CACHE_VALID_TIME = 86400000L;


    /*
    * cookie 默认有效期
    * */
    public static int DEFAULT_COOKIE_VALID_TIME = 60 * 60 * 24 * 30;

    /*
     * 高德获取地区接口地址
     */
    public static final String AMAP_REMOTE_URL = "https://restapi.amap.com/v3/ip";

    /*
    *  Redis Zset 中数据过期时间 当前业务周期（24h） + 缓冲容错周期（24h）
    * */
   public static final long TODAY_EXPIRETIME = 48;
}
