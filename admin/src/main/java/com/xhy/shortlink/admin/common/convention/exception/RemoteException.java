package com.xhy.shortlink.admin.common.convention.exception;


import com.xhy.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.xhy.shortlink.admin.common.convention.errorcode.IErrorCode;

/**
 * 远程服务调用异常
 */
public class RemoteException extends AbstractException {

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }
    // 新增的构造函数，专门用于处理 ResultUtils 中的 String 类型错误码
    public RemoteException(String code, String message) {
        this(message, null, new IErrorCode() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public String message() {
                return message;
            }
        });
    }
    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "RemoteException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}
