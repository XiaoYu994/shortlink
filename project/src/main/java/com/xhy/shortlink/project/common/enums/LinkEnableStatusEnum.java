package com.xhy.shortlink.project.common.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
* 短链接启用标识
* */
@RequiredArgsConstructor
public enum LinkEnableStatusEnum {
    /*
     * 启用
     * */
    ENABLE(0),
    /*
     * 未启用
     * */
    NOT_ENABLED(1),

    /*
    *  平台封禁
    * */
    BANNED(2),
    /*
     * 冻结（到期进入冻结期）
     * */
    FROZEN(3);
    @Getter
    private final int enableStatus;
}
