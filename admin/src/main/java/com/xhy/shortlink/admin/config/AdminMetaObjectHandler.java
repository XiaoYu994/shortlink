package com.xhy.shortlink.admin.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Date;

/*
* mybatisPlus自动填充
* */
@Slf4j
@Primary
@Component(value = "metaObjectHandlerByAdmin")
public class AdminMetaObjectHandler  implements MetaObjectHandler {

    /**
     * 插入时填充字段
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("插入时自动填充创建时间和更新时间");
        // 填充创建时间
        this.strictInsertFill(metaObject, "createTime", Date::new, Date.class);
        // 填充更新时间
        this.strictInsertFill(metaObject, "updateTime", Date::new, Date.class);
    }

    /**
     * 更新时填充字段
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("更新时自动填充更新时间");

        // 填充更新时间
        this.strictUpdateFill(metaObject, "updateTime",Date::new, Date.class);
    }
}
