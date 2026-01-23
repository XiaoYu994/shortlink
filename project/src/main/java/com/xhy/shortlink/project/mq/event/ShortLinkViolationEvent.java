package com.xhy.shortlink.project.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/*
*  发送风控通知事件
* */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkViolationEvent {
    private String fullShortUrl; // 违规链接
    private String gid;          // 归属组 (用于查找用户)
    private String reason;       // 违规原因 (色情/赌博等)
    private LocalDateTime time;  // 违规时间
    private Long userId; // 用户 id
}
