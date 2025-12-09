package com.xhy.shortlink.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

/**
 * MyBatis-Plus配置类
 */
@Slf4j
@Configuration
public class MybatisPlusConfig implements MetaObjectHandler {

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

    /**
     * 配置分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 设置最大单页限制数量，默认 500 条，-1 不受限制
        paginationInterceptor.setMaxLimit(1000L);
        // 设置溢出总页数后是否进行处理(默认不处理)
        paginationInterceptor.setOverflow(false);


        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
} 