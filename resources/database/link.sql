/*
 Navicat Premium Dump SQL

 Source Server         : 本机
 Source Server Type    : MySQL
 Source Server Version : 90500 (9.5.0)
 Source Host           : localhost:3306
 Source Schema         : link

 Target Server Type    : MySQL
 Target Server Version : 90500 (9.5.0)
 File Encoding         : 65001

 Date: 19/12/2025 14:11:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `t_group`
(
    `id`          bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `gid`         varchar(32)  DEFAULT NULL COMMENT '分组标识',
    `name`        varchar(64)  DEFAULT NULL COMMENT '分组名称',
    `username`    varchar(256) DEFAULT NULL COMMENT '创建分组用户名',
    `sort_order`  int(3) DEFAULT 0 COMMENT '分组排序',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime     DEFAULT NULL COMMENT '修改时间',
    `del_flag`    tinyint(1) DEFAULT 0 COMMENT '删除标识 0：未删除 1：已删除',
    PRIMARY KEY (`id`),
    KEY           `idx_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- ----------------------------
-- Table structure for t_group_unique
-- ----------------------------
CREATE TABLE `t_group_unique`
(
    `id`  bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `gid` varchar(32) DEFAULT NULL COMMENT '分组标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_unique_gid` (`gid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_link
-- ----------------------------
CREATE TABLE `t_link`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `domain` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '域名',
  `short_uri` varchar(8) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '短链接',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  `origin_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '原始链接',
  `gid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分组标识',
  `favicon` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '网站图标',
  `enable_status` tinyint(1) NULL DEFAULT 0 COMMENT '启用标识 （0：启用）（1：未启用）（2：平台封禁）',
  `created_type` tinyint(1) NULL DEFAULT 0 COMMENT '创建类型 0：接口 1：控制台',
  `valid_date_type` tinyint(1) NULL DEFAULT NULL COMMENT '有效期类型 0：永久有效 1：用户自定义',
  `valid_date` datetime NULL DEFAULT NULL COMMENT '有效期',
  `describe` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `total_pv` int(11) NULL DEFAULT 0 COMMENT '历史PV',
  `total_uv` int(11) NULL DEFAULT 0 COMMENT '历史UV',
  `total_uip` int(11) NULL DEFAULT 0 COMMENT '历史UIP',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '删除标识 0：未删除 1：已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_unique_full_short_url`(`full_short_url` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_link_access_logs
-- ----------------------------
CREATE TABLE `t_link_access_logs`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  `user` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户信息',
  `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'IP',
  `browser` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '浏览器',
  `os` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作系统',
  `network` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '访问网络',
  `device` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '访问设备',
  `locale` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '访问地区',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '删除标识 0：未删除 1：已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_full_short_url`(`full_short_url` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_link_access_stats
-- ----------------------------
CREATE TABLE `t_link_access_stats`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  `date` date NULL DEFAULT NULL COMMENT '日期',
  `pv` int NULL DEFAULT NULL COMMENT '访问量',
  `uv` int NULL DEFAULT NULL COMMENT '独立访问数',
  `uip` int NULL DEFAULT NULL COMMENT '独立IP数',
  `hour` int NULL DEFAULT NULL COMMENT '小时',
  `weekday` int NULL DEFAULT NULL COMMENT '星期',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识：0 未删除 1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_unique_access_stats`(`full_short_url` ASC, `hour` ASC, `date` ASC) USING BTREE,
  INDEX `idx_stats_today`(`date` ASC, `full_short_url` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_link_browser_stats
-- ----------------------------
CREATE TABLE `t_link_browser_stats`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  `date` date NULL DEFAULT NULL COMMENT '日期',
  `cnt` int NULL DEFAULT NULL COMMENT '访问量',
  `browser` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '浏览器',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0：未删除 1：已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_unique_browser_stats`(`full_short_url` ASC, `date` ASC, `browser` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_link_device_stats
-- ----------------------------
CREATE TABLE `t_link_device_stats`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  `date` date NULL DEFAULT NULL COMMENT '日期',
  `cnt` int NULL DEFAULT NULL COMMENT '访问量',
  `device` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '访问设备',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0：未删除 1：已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_unique_device_stats`(`full_short_url` ASC, `date` ASC, `device` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_link_goto
-- ----------------------------
CREATE TABLE `t_link_goto`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `gid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'default' COMMENT '分组标识',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_full_short_url` (`full_short_url`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- ----------------------------
-- Table structure for t_link_locale_stats
-- ----------------------------
CREATE TABLE `t_link_locale_stats`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  `date` date NULL DEFAULT NULL COMMENT '日期',
  `cnt` int NULL DEFAULT NULL COMMENT '访问量',
  `country` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '国家标识',
  `province` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '省份名称',
  `city` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '市名称',
  `adcode` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '城市编码',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0表示删除 1表示未删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_unique_locale_stats`(`full_short_url` ASC, `date` ASC, `adcode` ASC, `province` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_link_network_stats
-- ----------------------------
CREATE TABLE `t_link_network_stats`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  `date` date NULL DEFAULT NULL COMMENT '日期',
  `cnt` int NULL DEFAULT NULL COMMENT '访问量',
  `network` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '访问网络',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0：未删除 1：已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_unique_browser_stats`(`full_short_url` ASC, `date` ASC, `network` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_link_os_stats
-- ----------------------------
CREATE TABLE `t_link_os_stats`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `full_short_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整短链接',
  `date` date NULL DEFAULT NULL COMMENT '日期',
  `cnt` int NULL DEFAULT NULL COMMENT '访问量',
  `os` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作系统',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0表示删除 1表示未删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_unique_locale_stats`(`full_short_url` ASC, `date` ASC, `os` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_user
-- ----------------------------
CREATE TABLE `t_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `username` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名',
  `password` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '密码',
  `real_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '手机号',
  `mail` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `deletion_time` bigint(20) NULL DEFAULT NULL COMMENT '注销时间戳',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '删除标识 0：未删除 1：已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_unique_username`(`username` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `t_user_notification` (
                                       `id` bigint NOT NULL COMMENT 'ID',
                                       `user_id` bigint DEFAULT NULL COMMENT '用户ID',
                                       `type` tinyint(1) DEFAULT NULL COMMENT '通知类型: 0-系统通知 1-违规提醒',
                                       `title` varchar(64) DEFAULT NULL COMMENT '标题',
                                       `content` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '内容',
                                       `read_flag` tinyint(1) DEFAULT '0' COMMENT '是否已读: 0-未读 1-已读',
                                       `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户通知表';

SET FOREIGN_KEY_CHECKS = 1;
