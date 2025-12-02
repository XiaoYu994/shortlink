package com.xhy.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhy.shortlink.admin.dao.entity.UserDO;
import com.xhy.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.xhy.shortlink.admin.dto.resp.UserRespDTO;

/**
 * @Description:用户接口层
 * @Author: XiaoYu
 * @date: 2025/12/2$ 16:36$
 */
public interface UserService extends IService<UserDO> {

    /**
     * 根据用户名查询用户信息
     * @param username
     * @return 用户返回实体
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 判断用户名是否存在
     * @param username
     * @return 存在返回true 不存在返回false
     */
    Boolean hasUsername(String username);

    /**
     * 用户注册
     *
     * @param requestParam
     * @return
     */
    void register(UserRegisterReqDTO requestParam);

}
