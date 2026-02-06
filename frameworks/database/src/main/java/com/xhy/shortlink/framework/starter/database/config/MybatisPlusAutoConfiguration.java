/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xhy.shortlink.framework.starter.database.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.xhy.shortlink.framework.starter.database.handler.CustomIdGenerator;
import com.xhy.shortlink.framework.starter.database.handler.MyMetaObjectHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 自动装配配置类
 * <p>
 * 提供分页插件和元数据自动填充处理器的默认配置，
 * 业务模块可通过自定义 Bean 覆盖默认行为
 */
@Configuration
public class MybatisPlusAutoConfiguration {

    /**
     * 配置 MyBatis-Plus 拦截器链：
     * 1. 添加分页插件，数据库类型为 MySQL
     * 2. 单页最大限制 500 条，防止全表扫描
     * 3. 页码溢出不自动处理，由业务层控制
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(500L);
        paginationInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }

    /**
     * 注册元数据自动填充处理器，负责 createTime/updateTime 的自动写入
     *
     * @return 元数据填充处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler metaObjectHandler() {
        return new MyMetaObjectHandler();
    }

    /**
     * 注册自定义 ID 生成器，通过 SnowflakeIdUtil 静态工具类生成 ID
     *
     * @return MyBatis-Plus 自定义 ID 生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public IdentifierGenerator identifierGenerator() {
        return new CustomIdGenerator();
    }
}
