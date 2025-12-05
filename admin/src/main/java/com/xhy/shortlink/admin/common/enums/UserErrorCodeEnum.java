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
