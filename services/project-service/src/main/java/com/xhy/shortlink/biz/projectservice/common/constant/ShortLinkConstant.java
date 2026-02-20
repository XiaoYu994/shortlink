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

package com.xhy.shortlink.biz.projectservice.common.constant;

/**
 * 短链接业务常量
 *
 * @author XiaoYu
 */
public final class ShortLinkConstant {

    private ShortLinkConstant() {
    }

    /** 默认缓存有效期（毫秒），1 天 */
    public static final long DEFAULT_CACHE_VALID_TIME = 86400000L;

    /** 短链接创建前缀 */
    public static final String HTTP_PROTOCOL = "http://";

    /** Redis ZSet 数据过期时间（小时），业务周期 24h + 缓冲容错 24h */
    public static final long TODAY_EXPIRETIME = 48;

    /** UV Cookie 有效期（秒），30 天 */
    public static final int DEFAULT_COOKIE_VALID_TIME = 60 * 60 * 24 * 30;

    /** 短链接不存在时的跳转页面 */
    public static final String PAGE_NOT_FOUND = "/page/notfound";
}
