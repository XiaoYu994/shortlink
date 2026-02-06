package com.xhy.shortlink.framework.starter.bases;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/*
*  单例对象容器
*  提供一个线程安全的单例对象容器，类似于简化版的 spring 容器
*   - 有些对象不适合放入 Spring 容器（如第三方库的对象）
    - 需要延迟初始化的单例对象
    - 需要在非 Spring 环境中使用单例模式
    - 比传统的双重检查锁单例模式更简洁
使用场景
*   1. 懒加载创建单例对象
*   2. 缓存配置对象
*   3. 手动管理单例
*
*
* */
public class Singleton {

    private static final ConcurrentHashMap<String,Object> SINGLE_OBJECT_POOL = new ConcurrentHashMap<>();

    /*
    *  获取单例对象
    * */
    public static <T> T get(String key) {
        Object result = SINGLE_OBJECT_POOL.get(key);
        return result == null ? null : (T) result;
    }

    /*
    *  获取单例对象，不存在时通过 Supplier 创建
    * */
    public static <T> T get(String key, Supplier<T> supplier) {
        return (T) SINGLE_OBJECT_POOL.computeIfAbsent(key, k ->
            supplier.get());
    }


    /*
    *  放入单例对象（自定义key）
    * */
    public static void put(String key, Object value) {
        SINGLE_OBJECT_POOL.put(key, value);
    }

    /*
    *  放入单例对象，使用类名作为 key
    * */
    public static void put(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");        
        }
        put(value.getClass().getName(), value);
    }
}
