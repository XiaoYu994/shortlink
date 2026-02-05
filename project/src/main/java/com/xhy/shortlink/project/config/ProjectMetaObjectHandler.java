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

package com.xhy.shortlink.project.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class ProjectMetaObjectHandler implements MetaObjectHandler {
    /**
     * 插入时填充字段
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("插入时自动填充创建时间和更新时间");
        // 填充创建时间
        this.strictInsertFill(metaObject, "createTime", Date::new, Date.class);
        // 填充更新时间
        this.strictInsertFill(metaObject, "updateTime", Date::new, Date.class);
    }

    /**
     * 更新时填充字段
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("更新时自动填充更新时间");

        // 填充更新时间
        this.strictUpdateFill(metaObject, "updateTime",Date::new, Date.class);
    }
}
