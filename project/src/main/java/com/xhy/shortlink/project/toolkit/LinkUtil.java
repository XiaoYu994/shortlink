package com.xhy.shortlink.project.toolkit;


import cn.hutool.core.util.StrUtil;
import cn.hutool.http.useragent.UserAgentUtil;
import com.xhy.shortlink.project.common.convention.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;

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
            // 已经过期了不能返回0，抛异常，由调用方处理
            throw new ClientException("短链接已过期");
        }
        // 4. 【核心逻辑】取最小值
        // 如果剩余时间(比如3年) > 默认时间(1天)，就只存1天
        // 如果剩余时间(比如5分钟) < 默认时间(1天)，就存5分钟，防止缓存超期
        return Math.min(timeToLive, DEFAULT_CACHE_VALID_TIME);
    }

    /**
     * 获取用户真实IP
     *
     * @param request 请求
     * @return 用户真实IP
     */
    public static String getActualIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    /**
     * 获取用户访问操作系统
     *
     * @param request 请求
     * @return 访问操作系统
     */
    public static String getOs(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.toLowerCase().contains("windows")) {
            return "Windows";
        } else if (userAgent.toLowerCase().contains("mac")) {
            return "Mac OS";
        } else if (userAgent.toLowerCase().contains("linux")) {
            return "Linux";
        } else if (userAgent.toLowerCase().contains("android")) {
            return "Android";
        } else if (userAgent.toLowerCase().contains("iphone") || userAgent.toLowerCase().contains("ipad")) {
            return "iOS";
        } else {
            return "Unknown";
        }
    }

    /**
     * 获取用户访问浏览器
     *
     * @param request 请求
     * @return 访问浏览器
     */
    public static String getBrowser(HttpServletRequest request) {
        final String browser = UserAgentUtil.parse(request.getHeader("User-Agent")).getBrowser().toString();
        if(StrUtil.isEmpty( browser)) {
            return "未知";
        }
        return browser ;
    }

    /**
     * 获取用户访问设备
     *
     * @param request 请求
     * @return 访问设备
     */
    public static String getDevice(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.toLowerCase().contains("mobile")) {
            return "Mobile";
        }
        return "PC";
    }
}
