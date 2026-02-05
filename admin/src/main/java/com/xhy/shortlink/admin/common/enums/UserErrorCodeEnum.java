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

package com.xhy.shortlink.admin.common.enums;

import com.xhy.shortlink.admin.common.convention.errorcode.IErrorCode;

public enum UserErrorCodeEnum implements IErrorCode {

    USER_NULL("B000200", "用户不存在"),

    USER_NAME_EXIST("B000201", "用户名已存在"),
    USER_SAVE_ERROR("B000202", "用户注册失败"),
    USER_UPDATE_ERROR("B000203", "用户更新失败"),
    USER_DELETE_ERROR("B000204", "用户删除失败"),
    USER_QUERY_ERROR("B000205", "用户查询失败"),
    USER_PASSWORD_ERROR("B000206", "用户密码错误"),
    USER_LOGIN_ERROR("B000207", "用户登录失败"),
    USER_LOGOUT_ERROR("B000208", "用户登出失败"),
    USER_LOGIN_EXIT("B000209", "用户已登录"),
    USER_NOT_LOGIN("B000210", "用户未登录或用户Token不存在" );

    private final String code;

    private final String message;

    UserErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
