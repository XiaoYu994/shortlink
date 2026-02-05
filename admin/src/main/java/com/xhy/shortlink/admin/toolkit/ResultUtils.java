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
