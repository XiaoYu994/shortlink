package com.xhy.shortlink.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
*  前端返回的排序字段枚举
* */
@RequiredArgsConstructor
public enum OrderTagEnum {
    /*
     *  根据 pv
     * */
    TODAY_PV("todayPv"),

    /*
     * 根据 uv
     * */
    TODAY_UV("todayUv"),

    /*
     * 根据 uip
     * */
    TODAY_UIP("todayUip");

    @Getter
    private final String value;
}
