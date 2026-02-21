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

package com.xhy.shortlink.biz.userservice.toolkit;

import com.xhy.shortlink.framework.starter.convention.exception.RemoteException;
import com.xhy.shortlink.framework.starter.convention.result.Result;

import java.util.Objects;

/**
 * 远程调用结果校验工具类
 *
 * @author XiaoYu
 */
public class ResultUtils {

    /**
     * 校验远程调用结果，失败时抛出 {@link RemoteException}
     *
     * @param result 远程调用返回的 Result 对象
     * @param <T>    返回数据类型
     * @return 远程调用返回的业务数据
     */
    public static <T> T check(Result<T> result) {
        if (result == null) {
            throw new RemoteException("远程服务未响应");
        }
        if (!Objects.equals(result.getCode(), "0")) {
            throw new RemoteException(result.getMessage());
        }
        return result.getData();
    }
}
