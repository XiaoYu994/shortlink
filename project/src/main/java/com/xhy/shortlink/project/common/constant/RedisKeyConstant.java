package com.xhy.shortlink.project.common.constant;

import java.util.concurrent.ThreadLocalRandom;

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
     * 短链缓空缓存过期时间
     */
    public static long DEFAULT_CACHE_VALID_TIME_FOR_GOTO = 30 + ThreadLocalRandom.current().nextInt(10);

    /*
    * 短链接跳转锁 前缀key
    * */
    public static final String LOOK_GOTO_SHORT_LINK_KEY = "short_link_goto_lock_%s:";

    /*
     * 短链接统计判断是否新用户缓存标识
     */
    public static final String SHORT_LINK_STATS_UV_KEY = "short-link:stats:uv:";

    /*
     * 短链接统计判断是否新 IP 缓存标识
     */
    public static final String SHORT_LINK_STATS_UIP_KEY = "short-link:stats:uip:";


    /*
     * 短链接修改分组 ID 锁前缀 Key
     */
    public static final String LOCK_GID_UPDATE_KEY = "short-link_lock_update-gid_%s";

    /*
     * 短链接延迟队列消费统计 Key
     */
    public static final String DELAY_QUEUE_STATS_KEY = "short-link_delay-queue:stats";

    /**
     * 短链接监控消息保存队列 Topic 缓存标识
     */
    public static final String SHORT_LINK_STATS_STREAM_TOPIC_KEY = "short-link:stats-stream";

    /**
     * 短链接监控消息保存队列 Group 缓存标识
     */
    public static final String SHORT_LINK_STATS_STREAM_GROUP_KEY = "short-link:stats-stream:only-group";

}
