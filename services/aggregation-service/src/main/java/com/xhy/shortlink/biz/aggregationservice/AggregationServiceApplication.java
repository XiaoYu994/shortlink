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

package com.xhy.shortlink.biz.aggregationservice;

import com.xhy.shortlink.biz.projectservice.ShortlinkProjectApplication;
import com.xhy.shortlink.biz.riskservice.RiskServiceApplication;
import com.xhy.shortlink.biz.statsservice.StatsServiceApplication;
import com.xhy.shortlink.biz.userservice.ShortlinkUserApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/** 聚合 user / project / stats / risk 的生产进程。 */
@EnableDiscoveryClient
@EnableFeignClients("com.xhy.shortlink.biz.userservice.remote")
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ComponentScan(
        basePackages = {
                "com.xhy.shortlink.biz.userservice",
                "com.xhy.shortlink.biz.projectservice",
                "com.xhy.shortlink.biz.statsservice",
                "com.xhy.shortlink.biz.riskservice",
                "com.xhy.shortlink.biz.aggregationservice"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        ShortlinkUserApplication.class,
                        ShortlinkProjectApplication.class,
                        StatsServiceApplication.class,
                        RiskServiceApplication.class
                }),
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@MapperScan(
        value = {
                "com.xhy.shortlink.biz.userservice.dao.mapper",
                "com.xhy.shortlink.biz.projectservice.dao.mapper",
                "com.xhy.shortlink.biz.statsservice.dao.mapper",
                "com.xhy.shortlink.biz.riskservice.dao.mapper"
        },
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class AggregationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AggregationServiceApplication.class, args);
    }
}
