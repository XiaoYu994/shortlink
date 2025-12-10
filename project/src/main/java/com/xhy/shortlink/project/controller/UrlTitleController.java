package com.xhy.shortlink.project.controller;

import com.xhy.shortlink.project.common.convention.result.Result;
import com.xhy.shortlink.project.common.convention.result.Results;
import com.xhy.shortlink.project.service.UrlTitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
* 获取目标网站标题
* */
@RestController
@RequiredArgsConstructor
public class UrlTitleController {
    private final UrlTitleService UrlTitleService;

    @GetMapping("/api/short-link/v1/title")
    public Result<String> getTitle(@RequestParam("url") String url) {
        return Results.success(UrlTitleService.getPageTitle(url));
    }
}
