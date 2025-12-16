package com.xhy.shortlink.admin.common.constant;


/*
* Redis缓存常量
* */
public class RedisCacheConstant {


    /*
     * 用户注册分布式锁
     */
    public static final String LOCK_USER_REGISTER_KEY = "short-link:lock_user-register:";

    /*
    * 用户登录缓存 key
    * */
    public static final String LOGIN_USER_KEY = "short-link:login_user:";


    /*
     * 分组创建分布式锁
     */
    public static final String LOCK_GROUP_CREATE_KEY = "short-link:lock_group-create:%s";
}
