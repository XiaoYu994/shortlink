package com.xhy.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/*
 * 用户通知实体
 * */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_user_notification")
public class UserNotificationDO {

    /**
     * ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 通知类型: 0-系统通知 1-违规提醒
     */
    private Integer type;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 是否已读: 0-未读 1-已读
     */
    private Integer readFlag;

    /**
     * 创建时间
     * (配合 MyBatisPlus 自动填充使用，或者手动 set)
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
