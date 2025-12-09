package com.xhy.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.project.common.convention.result.Result;
import com.xhy.shortlink.project.common.convention.result.Results;
import com.xhy.shortlink.project.dto.req.ShortlinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortlinkPageReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortlinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortlinkPageRespDTO;
import com.xhy.shortlink.project.service.ShortlinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShortlinkController {
    private final ShortlinkService shortlinkService;

    /*
    * 创建短链接
    * */
    @PostMapping("/api/short-link/v1/create")
    public Result<ShortlinkCreateRespDTO> createShortlink(@RequestBody ShortlinkCreateReqDTO requestParam) {
        return Results.success(shortlinkService.createShortlink(requestParam));
    }

    /*
    * 分页查询短链接
    * */
    @GetMapping("/api/short-link/v1/page")
    public Result<IPage<ShortlinkPageRespDTO> > pageShortlink(ShortlinkPageReqDTO requestParam) {
        return Results.success(shortlinkService.pageShortlink(requestParam));
    }
}
