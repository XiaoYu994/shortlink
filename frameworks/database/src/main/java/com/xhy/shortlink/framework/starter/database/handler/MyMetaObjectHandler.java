package com.xhy.shortlink.framework.starter.database.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Date;

/**
 * MyBatis-Plus 元数据自动填充处理器
 * <p>
 * 配合 BaseDO 中 @TableField(fill=...) 注解，
 * 在插入和更新操作时自动填充时间字段
 */
@Slf4j
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充：
     * 1. createTime - 记录创建时间
     * 2. updateTime - 初始化为与创建时间一致
     * <p>
     * 使用 strictInsertFill 严格模式，仅当字段值为 null 时才填充，避免覆盖手动设置的值
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", Date::new, Date.class);
        this.strictInsertFill(metaObject, "updateTime", Date::new, Date.class);
    }

    /**
     * 更新时自动填充：
     * 1. updateTime - 更新为当前时间
     * <p>
     * 使用 strictUpdateFill 严格模式，仅当字段值为 null 时才填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", Date::new, Date.class);
    }
}
