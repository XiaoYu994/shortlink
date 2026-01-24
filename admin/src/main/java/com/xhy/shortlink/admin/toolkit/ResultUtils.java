package com.xhy.shortlink.admin.toolkit;

import com.xhy.shortlink.admin.common.convention.exception.RemoteException;
import com.xhy.shortlink.admin.common.convention.result.Result;

import java.util.Objects;

/*
*  远程调用异常处理
* */
public class ResultUtils {
    /**
     * 检查远程调用结果，如果失败则抛出异常
     */
    public static <T> T check(Result<T> result) {
        if (result == null) {
            throw new RemoteException("远程服务未响应");
        }
        if (!Objects.equals(result.getCode(), "0")) {
            throw new RemoteException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
