package com.xhy.shortlink.project.common.constant;

/*
*
* redis key 常量
* */
public class RedisKeyConstant {

    /*
    * 短链接跳转 前缀key
    * */
    public static final String GOTO_SHORT_LINK_KEY = "short_link_goto_%s:";

    /*
    * 短链接是否过期
    * */
    public static final String GOTO_IS_NULL_SHORT_LINK_KEY = "short_link_is-null_goto_%s:";

    /*
    * 短链接跳转锁 前缀key
    * */
    public static final String LOOK_GOTO_SHORT_LINK_KEY = "short_link_goto_lock_%s:";
}
