package com.xhy.shortlink.project.toolkit;


import java.util.Date;

import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.DEFAULT_CACHE_VALID_TIME;

/*
*
* 短链接工具类
* */
public class LinkUtil {
    /**
     * 获取缓存有效期
     * @param validDate 短链接的数据库截止日期
     * @return Redis 的 TTL (毫秒)
     */
    public static long getLinkCacheValidTime(Date validDate) {
        // 1. 如果是永久有效 (validDate == null)，直接返回默认的 1 天
        if (validDate == null) {
            return DEFAULT_CACHE_VALID_TIME;
        }
        // 2. 计算距离过期的剩余时间
        long timeToLive = validDate.getTime() - System.currentTimeMillis();
        // 3. 如果已经过期了（负数），返回 0 或抛异常，由调用方处理
        if (timeToLive <= 0) {
            return 0;
        }
        // 4. 【核心逻辑】取最小值
        // 如果剩余时间(比如3年) > 默认时间(1天)，就只存1天
        // 如果剩余时间(比如5分钟) < 默认时间(1天)，就存5分钟，防止缓存超期
        return Math.min(timeToLive, DEFAULT_CACHE_VALID_TIME);
    }
}
