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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import static com.xhy.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;


/*
* 用户接口实现层
* */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper,UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;

    // redisson  分布式锁更安全 有看门狗机制
    private  final RedissonClient redissonClient;
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
        // 分布式锁 将用户名条件做为锁
         RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
       try {
           if (lock.tryLock()) {
               final int insert = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
               if (insert < 1) {
                   throw new ClientException(UserErrorCodeEnum.USER_SAVE_ERROR);
               }
               // 添加布隆过滤器
               userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
               return;
           }
           throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
       } finally {
           lock.unlock();
       }

    }
}
