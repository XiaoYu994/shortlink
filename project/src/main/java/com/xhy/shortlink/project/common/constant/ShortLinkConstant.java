package com.xhy.shortlink.project.common.constant;

import java.util.concurrent.TimeUnit;

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

    /*
    *  Redis Zset 中数据过期时间 当前业务周期（24h） + 缓冲容错周期（24h）
    * */
   public static final String TODAY_EXPIRETIME = String.valueOf(TimeUnit.HOURS.toSeconds(48));
}
