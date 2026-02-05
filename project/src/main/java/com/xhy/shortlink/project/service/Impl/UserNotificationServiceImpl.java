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

package com.xhy.shortlink.project.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.dao.entity.UserNotificationDO;
import com.xhy.shortlink.project.dao.mapper.UserNotificationMapper;
import com.xhy.shortlink.project.service.UserNotificationService;
import org.springframework.stereotype.Service;

/*
*  用户通知接口层
* */
@Service
public class UserNotificationServiceImpl extends ServiceImpl<UserNotificationMapper, UserNotificationDO> implements UserNotificationService {
}
