package com.xhy.shortlink.framework.starter.bases.init;


import org.springframework.context.ApplicationEvent;

/*
*  应用初始化事件
* 规约事件，通过此事件可以查看业务系统所有初始化行为
* */
public class ApplicationInitializingEvent extends ApplicationEvent {
    public ApplicationInitializingEvent(Object source) {
        super(source);
    }
}
