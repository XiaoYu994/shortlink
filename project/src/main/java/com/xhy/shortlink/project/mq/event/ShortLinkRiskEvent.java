package com.xhy.shortlink.project.mq.event;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 *  AI 风控检测事件
 * */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkRiskEvent {
    /*
    *  完整短链接
    * */
    private String fullShortUrl;

    /**
     * 原始链接
     */
    private String originUrl;

    /**
     * 分组标识
     */
    private String gid;

    /*
    *  用户 id 后续发送用户通知要用
    * */
    private Long userId;
}
