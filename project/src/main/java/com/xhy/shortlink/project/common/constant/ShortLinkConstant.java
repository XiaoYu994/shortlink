package com.xhy.shortlink.project.common.constant;

public class ShortLinkConstant {

    /*
    * 默认缓存有效期 1天
    * */
    public static long DEFAULT_CACHE_VALID_TIME = 86400000L;


    /*
    * cookie 默认有效期
    * */
    public static int DEFAULT_COOKIE_VALID_TIME = 60 * 60 * 24 * 30;

    /*
     * 高德获取地区接口地址
     */
    public static final String AMAP_REMOTE_URL = "https://restapi.amap.com/v3/ip";
}
