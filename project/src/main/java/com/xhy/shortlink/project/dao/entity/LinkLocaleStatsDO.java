package com.xhy.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xhy.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/*
 * 短链接地区访问统计实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_link_locale_stats")
public class LinkLocaleStatsDO extends BaseDO {


    /*
     * ID
     */
    private Long id;

    /*
     * 完整短链接
     */
    private String fullShortUrl;

    /*
     * 分组标识
     */
    private String gid;

    /*
     * 日期
     */
    private Date date;

    /*
     * 访问量
     */
    private Integer cnt;

    /*
     * 国家标识
     */
    private String country;

    /*
     * 省份名称
     */
    private String province;

    /*
     * 市名称
     */
    private String city;

    /*
     * 城市编码
     */
    private String adcode;
}