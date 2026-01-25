package com.xhy.shortlink.project.mq.event;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/*
 * 短链接统计事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsRecordEvent {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 访问用户IP
     */
    private String remoteAddr;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作设备
     */
    private String device;

    /**
     * 网络
     */
    private String network;

    /**
     * UV
     */
    private String uv;

    /*
    *  gid
    * */
    private String gid;


    /**
     * 当前时间
     */
    private Date currentDate;

    /*
    *  消息 key 唯一标识
    * */
    private String eventId;
}
