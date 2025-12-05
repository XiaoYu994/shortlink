package com.xhy.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xhy.shortlink.admin.common.database.BaseDO;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("t_group")
public class GroupDO extends BaseDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id;

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 创建分组用户名
     */
    private String username;

    /**
     * 分组排序
     */
    private Integer sortOrder;
}