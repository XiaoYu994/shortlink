package com.xhy.shortlink.framework.starter.database.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

/**
 * 数据库持久层基础实体类
 * <p>
 * 所有业务实体类应继承此类，自动获得创建时间、修改时间、删除标识字段
 */
@Data
public class BaseDO {

    /**
     * 创建时间，插入时由 MyMetaObjectHandler 自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 修改时间，插入和更新时由 MyMetaObjectHandler 自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 删除标识 0：未删除 1：已删除
     */
    private Integer delFlag;
}
