package com.xhy.shortlink.framework.stater.designpattern.builder;

import java.io.Serializable;

/**
 * Builder 模型抽象接口
 */
public interface Builder<T> extends Serializable {

    /**
     * 构建方法
     *
     * @return 构建后的对象
     */
    T build();
}
