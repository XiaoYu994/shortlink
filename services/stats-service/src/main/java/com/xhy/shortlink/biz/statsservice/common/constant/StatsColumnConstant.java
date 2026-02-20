package com.xhy.shortlink.biz.statsservice.common.constant;

/**
 * 统计查询 Mapper 返回字段名 & UV 类型常量
 */
public final class StatsColumnConstant {

    private StatsColumnConstant() {
    }

    // ---------- Mapper 返回列别名 ----------

    public static final String COL_COUNT = "count";
    public static final String COL_IP = "ip";
    public static final String COL_BROWSER = "browser";
    public static final String COL_OS = "os";
    public static final String COL_USER = "user";
    public static final String COL_UV_TYPE = "uvType";
    public static final String COL_OLD_USER_CNT = "oldUserCnt";
    public static final String COL_NEW_USER_CNT = "newUserCnt";

    // ---------- UV 类型值 ----------

    public static final String UV_TYPE_NEW = "newUser";
    public static final String UV_TYPE_OLD = "oldUser";
    public static final String UV_TYPE_OLD_LABEL = "旧访客";
}
