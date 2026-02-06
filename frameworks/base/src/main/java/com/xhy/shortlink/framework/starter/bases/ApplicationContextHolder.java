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

package com.xhy.shortlink.framework.starter.bases;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Spring 容器工具类，解决非 Spring 管理类获取 Bean 的问题
 * <p>
 * Spring 的依赖注入只能在 Spring 管理的 Bean 中使用 @Autowired，
 * 在静态方法、工具类、线程中无法使用依赖注入。
 * 这个工具类提供了在任何地方获取 Spring Bean 的能力。
 */
public class ApplicationContextHolder implements ApplicationContextAware{

    private static ApplicationContext CONTEXT;
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextHolder.CONTEXT = applicationContext;
    }

    /*
    *  通过类型获取容器 bean
    * */
    public static <T> T getBean(Class<T> clazz){
        return CONTEXT.getBean(clazz);
    }

    /*
    *  通过名称获取容器 bean
    * */
    public static Object getBean(String name){
        return CONTEXT.getBean(name);
    }

    /*
    *  通过名称和类型获取容器 bean
    * */
    public static <T> T getBean(String name, Class<T> clazz){
        return CONTEXT.getBean(name, clazz);
    }

    /*
    *  获取某个类型的所有 bean
    * */
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz){
        return CONTEXT.getBeansOfType(clazz);
    }

    /*
    *  查找 bean 是否有注解
    * */
    public static <A extends Annotation> A findAnnotationOnBean(String beanName,Class<A> annotationClass){
        return CONTEXT.findAnnotationOnBean(beanName,annotationClass);
    }

    /*
    *  获取应用程序上下文
    * */
    public static ApplicationContext getContext(){
        return CONTEXT;
    }
}
