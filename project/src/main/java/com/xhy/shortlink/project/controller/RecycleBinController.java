package com.xhy.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.project.common.convention.result.Result;
import com.xhy.shortlink.project.common.convention.result.Results;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkRecycleBinPageRespDTO;
import com.xhy.shortlink.project.service.Impl.RecycleBinServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/*
* 回收站管理接口
* */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {
    private final RecycleBinServiceImpl recycleBinService;

    /*
    * 短链接加入回收站
    * */
    @PostMapping("/api/short-link/v1/recycle-bin/save")
    public Result<Void> recycleBinSave(@RequestBody ShortLinkRecycleBinSaveReqDTO requestParam) {
        recycleBinService.recycleBinSave(requestParam);
        return Results.success();
    }

    /*
    * 分页查询回收站链接
    * */
    @GetMapping("/api/short-link/v1/recycle-bin/page")
    public Result<IPage<ShortLinkRecycleBinPageRespDTO>> pageRecycleBinShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return Results.success(recycleBinService.pageShortlink(requestParam));
    }
}
