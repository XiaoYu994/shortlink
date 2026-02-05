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

package com.xhy.shortlink.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
*  前端返回的排序字段枚举
* */
@RequiredArgsConstructor
public enum OrderTagEnum {
    /*
     *  根据 pv
     * */
    TODAY_PV("todayPv"),

    /*
     * 根据 uv
     * */
    TODAY_UV("todayUv"),

    /*
     * 根据 uip
     * */
    TODAY_UIP("todayUip");

    @Getter
    private final String value;
}
