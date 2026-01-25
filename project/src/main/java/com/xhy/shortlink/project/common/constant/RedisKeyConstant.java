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
    public static final String GOTO_SHORT_LINK_KEY = "short-link:goto:%s:";

    /*
    * 短链接是否过期
    * */
    public static final String GOTO_IS_NULL_SHORT_LINK_KEY = "short-link:goto:is-null:%s:";

    /*
     * 短链缓空缓存过期时间
     */
    public static long DEFAULT_CACHE_VALID_TIME_FOR_GOTO = 30 + ThreadLocalRandom.current().nextInt(10);

    /*
    * 短链接跳转 锁 前缀key
    * */
    public static final String LOOK_GOTO_SHORT_LINK_KEY = "short-link:lock:goto:%s:";

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
    public static final String LOCK_GID_UPDATE_KEY = "short-link:lock:update-gid:%s";

    /*
     * 短链接延迟队列消费统计 Key
     */
    public static final String DELAY_QUEUE_STATS_KEY = "short-link:delay-queue:stats";

    /**
     * 短链接监控消息保存队列 Topic 缓存标识
     */
    public static final String SHORT_LINK_STATS_STREAM_TOPIC_KEY = "short-link:stats-stream";

    /**
     * 短链接监控消息保存队列 Group 缓存标识
     */
    public static final String SHORT_LINK_STATS_STREAM_GROUP_KEY = "short-link:stats-stream:only-group";

    /*
    * 分布式锁创建短链接
    * */
    public static final String SHORT_LINK_CREATE_LOCK_KEY =  "short-link:create-lock";

    /*
     * 清除缓存广播频道
     * */
    public static final String CHANNEL_TOPIC_KEY = "short-link:cache-invalidate:topic";

    /*
     * AI 风控检测 Topic 缓存标识
     * */
    public static final String RISK_CHECK_STREAM_TOPIC_KEY = "short-link:risk-stream";

    /*
     * AI 风控检测 Group 缓存标识
     * */
    public static final String RISK_CHECK_STREAM_GROUP_KEY = "short-link:risk-stream:only-group";


    /*
     * 发送风控通知 topic 缓存标识
     * */
    public static final String NOTIFY_STREAM_TOPIC_KEY = "short-link:notify-stream";

    /*
     * 发送风控通知 group 缓存标识
     * */
    public static final String NOTIFY_STREAM_GROUP_KEY = "short-link:notify-stream:only-group";

    /*
    *  统计今日 pv uv uip 数据 {%s} 使用 Hash Tag ，确保Redis Cluster (集群模式) 下的可用性
    * */
    public static final String RANK_KEY = "short-link:rank:%s:{%s}:%s";


}
