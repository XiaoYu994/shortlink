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
 * 统计查询 Mapper 返回字段名 & UV 类型常量
 */
public final class StatsColumnConstant {

    private StatsColumnConstant() {
    }

    // ---------- Mapper 返回列别名 ----------

    public static final String COL_COUNT = "count";
    public static final String COL_IP = "ip";
    public static final String COL_BROWSER = "browser";
    public static final String COL_OS = "os";
    public static final String COL_USER = "user";
    public static final String COL_UV_TYPE = "uvType";
    public static final String COL_OLD_USER_CNT = "oldUserCnt";
    public static final String COL_NEW_USER_CNT = "newUserCnt";

    // ---------- UV 类型值 ----------

    public static final String UV_TYPE_NEW = "newUser";
    public static final String UV_TYPE_OLD = "oldUser";
    public static final String UV_TYPE_OLD_LABEL = "旧访客";
}
