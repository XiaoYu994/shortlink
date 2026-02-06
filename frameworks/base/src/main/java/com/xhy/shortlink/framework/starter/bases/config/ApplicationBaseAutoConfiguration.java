package com.xhy.shortlink.framework.starter.bases.config;

import com.xhy.shortlink.framework.starter.bases.ApplicationContextHolder;
import com.xhy.shortlink.framework.starter.bases.init.ApplicationContentPostProcessor;
import com.xhy.shortlink.framework.starter.bases.safa.FastJsonSafeMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/*
*  应用基础自动装配
* */
public class ApplicationBaseAutoConfiguration {

    @Bean
    // 容器中不存在这个 bean 才会创建
    @ConditionalOnMissingBean
    public ApplicationContextHolder shortlinkApplicationContextHolder() {
        return new ApplicationContextHolder();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApplicationContentPostProcessor shortlinkApplicationContentPostProcessor(ApplicationContext applicationContext) {
        return new ApplicationContentPostProcessor(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "framework.fastjson.safe-mode", havingValue = "true")
    public FastJsonSafeMode shortlinkFastJsonSafeMode() {
        return new FastJsonSafeMode();
    }
}
