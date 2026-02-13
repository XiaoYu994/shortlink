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

package com.xhy.shortlink.biz.userservice.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.biz.userservice.dao.entity.UserDO;
import com.xhy.shortlink.biz.userservice.dao.mapper.UserMapper;
import com.xhy.shortlink.biz.userservice.dto.req.UserLoginReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.biz.userservice.dto.req.UserUpdateReqDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserLoginRespDTO;
import com.xhy.shortlink.biz.userservice.dto.resp.UserRespDTO;
import com.xhy.shortlink.biz.userservice.service.GroupService;
import com.xhy.shortlink.biz.userservice.service.UserService;
import com.xhy.shortlink.framework.starter.bases.constant.UserConstant;
import com.xhy.shortlink.framework.starter.common.enums.DelEnum;
import com.xhy.shortlink.framework.starter.common.toolkit.BeanUtil;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.xhy.shortlink.biz.userservice.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.xhy.shortlink.framework.starter.convention.errorcode.BaseErrorCode.USER_EXIST_ERROR;
import static com.xhy.shortlink.framework.starter.convention.errorcode.BaseErrorCode.USER_NAME_ERROR;
import static com.xhy.shortlink.framework.starter.convention.errorcode.BaseErrorCode.USER_NAME_EXIST_ERROR;
import static com.xhy.shortlink.framework.starter.convention.errorcode.BaseErrorCode.USER_NOT_LOGIN_ERROR;
import static com.xhy.shortlink.framework.starter.convention.errorcode.BaseErrorCode.USER_PASSWORD_ERROR;
import static com.xhy.shortlink.framework.starter.convention.errorcode.BaseErrorCode.USER_REGISTER_ERROR;

/**
 * 用户接口实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final GroupService groupService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        final LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDelFlag, DelEnum.NORMAL.getCode());
        final UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException(USER_EXIST_ERROR);
        }
        return BeanUtil.convert(userDO, UserRespDTO.class);
    }

    @Override
    public Boolean hasUsername(String username) {
        return userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterReqDTO requestParam) {
        if (Boolean.TRUE.equals(hasUsername(requestParam.getUsername()))) {
            throw new ClientException(USER_NAME_EXIST_ERROR);
        }
        RLock lock = redissonClient.getLock(String.format(LOCK_USER_REGISTER_KEY, requestParam.getUsername()));
        if (!lock.tryLock()) {
            throw new ClientException(USER_NAME_EXIST_ERROR);
        }
        try {
            UserDO userDO = BeanUtil.convert(requestParam, UserDO.class);
            userDO.setPassword(passwordEncoder.encode(requestParam.getPassword()));
            if (baseMapper.insert(userDO) < 1) {
                throw new ClientException(USER_REGISTER_ERROR);
            }
            // 创建默认分组
            groupService.saveGroup(requestParam.getUsername(),"默认分组");
            // 用户名加入布隆过滤器
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
                }
            });
        } catch (DuplicateKeyException ex) {
            throw new ClientException(USER_NAME_EXIST_ERROR);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateUser(UserUpdateReqDTO requestParam) {
        UserDO userDO = BeanUtil.convert(requestParam, UserDO.class);
        if (requestParam.getPassword() != null) {
            userDO.setPassword(passwordEncoder.encode(requestParam.getPassword()));
        }
        final LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getDelFlag, DelEnum.NORMAL.getCode());
        baseMapper.update(userDO, updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        if (Boolean.FALSE.equals(hasUsername(requestParam.getUsername()))) {
            throw new ClientException(USER_NAME_ERROR);
        }
        final LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getDelFlag, DelEnum.NORMAL.getCode());
        final UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null || !passwordEncoder.matches(requestParam.getPassword(), userDO.getPassword())) {
            throw new ClientException(USER_PASSWORD_ERROR);
        }
        StpUtil.login(userDO.getId());
        StpUtil.getSession()
                .set(UserConstant.USER_ID_KEY, String.valueOf(userDO.getId()))
                .set(UserConstant.USER_NAME_KEY, userDO.getUsername())
                .set(UserConstant.REAL_NAME_KEY, userDO.getRealName());
        return new UserLoginRespDTO(StpUtil.getTokenValue());
    }

    @Override
    public Boolean checkLogin(String token) {
        return StpUtil.getLoginIdByToken(token) != null;
    }

    @Override
    public void logout(String token) {
        if (Boolean.FALSE.equals(checkLogin(token))) {
            throw new ClientException(USER_NOT_LOGIN_ERROR);
        }
        StpUtil.logoutByTokenValue(token);
    }
}
