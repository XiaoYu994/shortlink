package com.xhy.shortlink.project.toolkit;


import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;

import java.util.Date;
import java.util.Optional;

import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.DEFAULT_CACHE_VALID_TIME;

/*
*
* 短链接工具类
* */
public class LinkUtil {
    /*
    * 如果当前用户传递的永久有效期就是一个月的缓存
    * 如果设置了有效期就从当前时间往后推
    * */
    public static long getLinkCacheValidTime(Date validDate) {
        return Optional.ofNullable(validDate)
                .map(each -> DateUtil.between(new Date(), each, DateUnit.MS))
                .orElse(DEFAULT_CACHE_VALID_TIME);
    }
}
