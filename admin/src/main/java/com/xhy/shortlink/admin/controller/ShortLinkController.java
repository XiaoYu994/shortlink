package com.xhy.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.common.convention.result.Results;
import com.xhy.shortlink.admin.remote.ShortLinkRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/*
* 后台调用短链接服务
* */
@RestController
public class ShortLinkController {
    /*
    * TODO 后续重构为SpringCloud Feign调用
    * */
   ShortLinkRemoteService shortlinkRemoteService = new ShortLinkRemoteService(){
    };

    /*
     * 分页查询短链接
     * */
    @GetMapping("/api/short-link/admin/v1/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageShortlink(ShortLinkPageReqDTO requestParam) {
        return Results.success(shortlinkRemoteService.pageShortlink(requestParam));
    }

    /*
     * 创建短链接
     * */
    @PostMapping("/api/short-link/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortlink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortlinkRemoteService.createShortlink(requestParam));
    }
}
