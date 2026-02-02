package com.xhy.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * 历史短链接路由实体
 * */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_link_goto_history")
public class ShortLinkGoToHistoryDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 完整短链接
     */
    private String fullShortUrl;
}
