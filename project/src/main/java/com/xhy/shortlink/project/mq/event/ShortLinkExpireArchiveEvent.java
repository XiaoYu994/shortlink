package com.xhy.shortlink.project.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


/*
*  过期短链接归档事件
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkExpireArchiveEvent {

    public enum Stage {
        FREEZE,
        ARCHIVE
    }

    private String eventId;

    private String gid;

    private String fullShortUrl;

    private Date expireAt;

    private Long userId;

    private Stage stage;
}
