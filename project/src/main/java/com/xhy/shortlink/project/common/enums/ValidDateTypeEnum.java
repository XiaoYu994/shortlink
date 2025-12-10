package com.xhy.shortlink.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
* 短链接有效期类型枚举
* */
@RequiredArgsConstructor
public enum ValidDateTypeEnum {
    /*
    * 永久有效
    * */
    PERMANENT(0),
    /*
    * 用户自定义
    * */
    CUSTOM(1);

    @Getter
    private final int type;
}
