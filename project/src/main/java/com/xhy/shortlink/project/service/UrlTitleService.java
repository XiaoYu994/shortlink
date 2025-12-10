package com.xhy.shortlink.project.service;

/*
* 获取目标网站标题接口
* */
public interface UrlTitleService {

    /**
    * 获取目标网站标题
     * @param url 目标网站地址
     * @return 目标网站标题
    * */
    String getPageTitle(String url);
}
