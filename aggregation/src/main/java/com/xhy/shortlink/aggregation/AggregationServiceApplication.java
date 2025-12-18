package com.xhy.shortlink.aggregation;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


/*
* 聚合服务启动类
* */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
        "com.xhy.shortlink.admin",
        "com.xhy.shortlink.project",
        "com.xhy.shortlink.aggregation"
})
@MapperScan(value = {
        "com.xhy.shortlink.project.dao.mapper",
        "com.xhy.shortlink.admin.dao.mapper"
})
public class AggregationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AggregationServiceApplication.class, args);
    }
}
