package com.xhy.shortlink.project.common.constant;

/*
* RocketMQ 常量类
* */
public class RocketMQConstant {

    /**
     *  清楚本地缓存 topic
     */
    public final static String CACHE_INVALIDATE_TOPIC = "short_link_project_cache_invalidate_topic";

    /**
     *  清楚本地缓存 group
     */
    public final static String CACHE_INVALIDATE_GROUP = "short_link_project_cache_invalidate_group";


    /**
     *  清楚本地缓存 tag
     */
    public final static String CACHE_INVALIDATE_TAG = "invalidate";


    /**
     *  短链接监控 topic
     */
    public final static String STATIC_TOPIC = "short_link_project_static_topic";

    /**
     *  短链接监控 group
     */
    public final static String STATIC_GROUP = "short_link_project_static_group";

    /**
     *   AI 风控审核消息 topic
     */
    public final static String RISK_CHECK_TOPIC = "short_link_project_risk_check_topic";

    /**
     *   AI 风控审核消息 group
     */
    public final static String RISK_CHECK_GROUP = "short_link_project_risk_check_group";
}
