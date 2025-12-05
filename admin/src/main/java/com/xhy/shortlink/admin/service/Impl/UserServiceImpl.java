package com.xhy.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.admin.common.convention.exception.ClientException;
import com.xhy.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.xhy.shortlink.admin.dao.entity.UserDO;
import com.xhy.shortlink.admin.dao.mapper.UserMapper;
import com.xhy.shortlink.admin.dto.req.UserLoginReqDTO;
import com.xhy.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.xhy.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.xhy.shortlink.admin.dto.resp.UserRespDTO;
import com.xhy.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.xhy.shortlink.admin.common.constant.RedisCacheConstant.LOGIN_USER_KEY;


/*
* 用户接口实现层
* */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper,UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final StringRedisTemplate stringRedisTemplate;
    // redisson  分布式锁更安全 有看门狗机制
    private  final RedissonClient redissonClient;
    @Override
    public UserRespDTO getUserByUsername(String username) {

        final LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class).
                                                        eq(UserDO::getUsername, username);
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

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        // TODO 判断用户是否登录 登录之后才能去修改用户信息
        // 根据用户名更新用户消息
        // 分片表是根据username去做分片的所以使用 username作为查询条件
        final LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class).
                                                                    eq(UserDO::getUsername, requestParam.getUsername());
        final int update = baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), updateWrapper);
        if (update < 1) {
            throw new ClientException(UserErrorCodeEnum.USER_UPDATE_ERROR);
        }
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        if (hasUsername(requestParam.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        final LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class).
                                                                eq(UserDO::getUsername, requestParam.getUsername())
                                                                .eq(UserDO::getPassword, requestParam.getPassword());
        final UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            // 用户名存在在布隆过滤器中，但是密码错误
            throw new ClientException(UserErrorCodeEnum.USER_PASSWORD_ERROR);
        }
        // 不能重复登录
        final Boolean hasLogin = stringRedisTemplate.hasKey(LOGIN_USER_KEY + requestParam.getUsername());
        // 装箱类型会有空指针问题
        if (hasLogin != null && hasLogin) {
            throw new ClientException(UserErrorCodeEnum.USER_LOGIN_EXIT);
        }

        // 登录成功后保存用户信息
        /*
         * 使用hasKey结果保存用户信息
         * key : Login_ + username
         * value :
         *       key : token (uuid生成)
         *       value : JSON.toString(userDO)
         * */
        // 1.生成uuid
        String uuid = UUID.randomUUID().toString();
        // 2.保存用户信息
        stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + requestParam.getUsername(), uuid, JSON.toJSONString(userDO));
        // 3.设置过期时间 30分钟
        stringRedisTemplate.expire(LOGIN_USER_KEY + requestParam.getUsername(), 30, TimeUnit.MINUTES);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        return stringRedisTemplate.opsForHash().hasKey(LOGIN_USER_KEY + username, token);
    }

    @Override
    public Boolean logout(String username, String token) {
        if (!checkLogin(username, token)) {
            throw new ClientException(UserErrorCodeEnum.USER_NOT_LOGIN);
        }
       return stringRedisTemplate.opsForHash().delete(LOGIN_USER_KEY + username, token) > 0;
    }
}
