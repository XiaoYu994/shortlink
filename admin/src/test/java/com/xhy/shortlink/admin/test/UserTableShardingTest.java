package com.xhy.shortlink.admin.test;

public class UserTableShardingTest {
    public static final String SQL ="CREATE TABLE `t_link_%d` (\n" +
            "  `id` bigint NOT NULL COMMENT 'ID ',\n" +
            "  `domain` varchar(128) DEFAULT NULL COMMENT '域名',\n" +
            "  -- 优化1: 统一字符集为 utf8mb4，保留 _bin 区分大小写\n" +
            "  `short_uri` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '短链接',\n" +
            "  `full_short_url` varchar(128) DEFAULT NULL COMMENT '完整短链接',\n" +
            "  `origin_url` varchar(1024) DEFAULT NULL COMMENT '原始链接',\n" +
            "  `gid` varchar(32) DEFAULT NULL COMMENT '分组标识',\n" +
            "  `favicon` varchar(256) DEFAULT NULL COMMENT '网站图标',\n" +
            "  `enable_status` tinyint(1) DEFAULT '0' COMMENT '启用标识 （0：启用）（1：未启用）(2:平台封禁)',\n" +
            "  `created_type` tinyint(1) DEFAULT '0' COMMENT '创建类型 0：接口 1：控制台',\n" +
            "  `valid_date_type` tinyint(1) DEFAULT NULL COMMENT '有效期类型 0：永久有效 1：用户自定义',\n" +
            "  `valid_date` datetime DEFAULT NULL COMMENT '有效期',\n" +
            "  -- 优化2: 字段名修改，避免关键字冲突\n" +
            "  `description` varchar(1024) DEFAULT NULL COMMENT '描述',\n" +
            "  -- 优化3: PV 改为 bigint 防止溢出\n" +
            "  `total_pv` bigint DEFAULT '0' COMMENT '历史PV',\n" +
            "  `total_uv` int DEFAULT '0' COMMENT '历史UV',\n" +
            "  `total_uip` int DEFAULT '0' COMMENT '历史UIP',\n" +
            "  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n" +
            "  `update_time` datetime DEFAULT NULL COMMENT '修改时间',\n" +
            "  `del_flag` tinyint(1) DEFAULT '0' COMMENT '删除标识 0：未删除 1：已删除',\n" +
            "  `last_access_time` datetime DEFAULT NULL COMMENT '最后访问时间',\n" +
            "  \n" +
            "  PRIMARY KEY (`id`),\n" +
            "  -- 核心业务唯一索引\n" +
            "  UNIQUE KEY `idx_unique_full_short_url` (`full_short_url`),\n" +
            "  -- 优化4: 配合瀑布流分页查询 (查询无流量数据)\n" +
            "  KEY `idx_gid_last_access` (`gid`,`last_access_time`),\n" +
            "  -- 优化5: 配合默认分页查询 (按创建时间倒序)\n" +
            "  KEY `idx_gid_create_time` (`gid`,`create_time`),\n" +
            "  -- 辅助索引: 如果经常有按 del_flag 或 enable_status 筛选，可以考虑放入联合索引，但目前这样也够用\n" +
            "  KEY `idx_gid_enable_del` (`gid`, `enable_status`, `del_flag`) \n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;";

    public static void main(String[] args) {
        for (int i = 0; i < 16; i++) {
            System.out.printf(SQL.formatted(i));
        }
    }
}
