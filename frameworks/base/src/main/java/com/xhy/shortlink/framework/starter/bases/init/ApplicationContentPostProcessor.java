package com.xhy.shortlink.framework.starter.bases.init;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

import java.util.concurrent.atomic.AtomicBoolean;

/*
*  应用初始化后置处理器，防止Spring事件被多次执行
*    - Spring 的 ApplicationReadyEvent 可能被触发多次（如在测试环境）
    - 提供一个统一的、确保只执行一次的初始化入口
    - 方便业务系统统一管理初始化逻辑
* */
@RequiredArgsConstructor
public class ApplicationContentPostProcessor implements ApplicationListener<ApplicationReadyEvent>{

    private final ApplicationContext applicationContext;
    private final AtomicBoolean executeOnlyOnce = new AtomicBoolean(false);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (executeOnlyOnce.compareAndSet(false, true)) {
            return;
        }
        // 发布自定义的初始化事件
        applicationContext.publishEvent(new ApplicationInitializingEvent(this));
    }
}
