package com.xhy.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.admin.common.convention.exception.ClientException;
import com.xhy.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.xhy.shortlink.admin.dao.entity.UserDO;
import com.xhy.shortlink.admin.dao.mapper.UserMapper;
import com.xhy.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.admin.dto.resp.UserRespDTO;
import com.xhy.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


/*
* 用户接口实现层
* */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper,UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    @Override
    public UserRespDTO getUserByUsername(String username) {

        final LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class).eq(UserDO::getUsername, username);
        final UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        final UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    @Override
    public Boolean hasUsername(String username) {
        // 添加布隆过滤器 用户名存在就会返回true 取反得false 用户名不可用
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {
        if(!hasUsername(requestParam.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }
        final int insert = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
        if (insert < 1) {
            throw new ClientException(UserErrorCodeEnum.USER_SAVE_ERROR);
        }
        // 添加布隆过滤器
        userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
    }
}
