package com.xhy.shortlink.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

public class ShortlinkAdminApplication {

    @SpringBootApplication
    @MapperScan("com.xhy.shortlink.admin.dao.mapper")
    public static class Application {
        public static void main(String[] args) {
            SpringApplication.run(Application.class, args);
        }
    }
}
