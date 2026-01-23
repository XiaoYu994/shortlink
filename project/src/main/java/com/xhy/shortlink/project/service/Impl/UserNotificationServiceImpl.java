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
