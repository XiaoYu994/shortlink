package com.xhy.shortlink.admin.controller;

import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.remote.ShortLinkRemoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
* 获取目标网站标题控制器
* */
@RestController
public class UrlTitleController {
    /*
     * TODO 后续重构为SpringCloud Feign调用
     * */
    ShortLinkRemoteService shortlinkRemoteService = new ShortLinkRemoteService(){
    };


    /*
    * 获取目标网站标题
    * */
    @GetMapping("/api/short-link/admin/v1/title")
    public Result<String> getTitle(@RequestParam("url") String url) {
        return shortlinkRemoteService.getPageTitle(url);
    }
}
