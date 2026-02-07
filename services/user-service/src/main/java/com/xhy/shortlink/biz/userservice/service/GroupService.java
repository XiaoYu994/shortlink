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

package com.xhy.shortlink.biz.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.biz.userservice.dao.entity.GroupDO;

/**
 * 短链分组接口层
 */
public interface GroupService extends IService<GroupDO> {

    /**
     * 新增短链接分组
     * @param groupName 创建分组名称
     * */
    void saveGroup(String groupName);

    /**
     * 新增短链接分组
     * @param groupName 创建分组名称
     * @param username 创建分组用户
     * */
    void saveGroup(String username,String groupName);
}
