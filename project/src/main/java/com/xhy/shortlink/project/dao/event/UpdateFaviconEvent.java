package com.xhy.shortlink.project.dao.event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/*
* 更新网站图标的事件
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateFaviconEvent {
    private String fullShortUrl; // 唯一标识
    private String gid;          // 唯一标识 (分库分表键)
    private String originUrl;    // 新的原始链接 (用于爬取)
}