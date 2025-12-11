package com.xhy.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.common.convention.result.Results;
import com.xhy.shortlink.admin.remote.ShortLinkRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkRecycleBinPageRespDTO;
import com.xhy.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/*
* 后管调用中台回收站接口
* */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {
    /*
     * TODO 后续重构为SpringCloud Feign调用
     * */
    ShortLinkRemoteService shortlinkRemoteService = new ShortLinkRemoteService(){
    };
    private final RecycleBinService recycleBinService;

    /*
    * 后管调用中台短链接回收接口
    * */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/save")
    public Result<Void> recycleBinSave(@RequestBody ShortLinkRecycleBinSaveReqDTO requestParam) {
        shortlinkRemoteService.RecycleBinSave(requestParam);
        return Results.success();
    }

    /*
    * 后管调用中台回收链接分页查询接口
    * */
    @GetMapping("/api/short-link/admin/v1/recycle-bin/page")
    public Result<IPage<ShortLinkRecycleBinPageRespDTO>> pageRecycleShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return recycleBinService.pageRecycleShortlink(requestParam);
    }

    /*
     * 后管调用中台恢复回收站链接接口
     * */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/recover")
    public Result<Void> recoverShortlink(@RequestBody ShortLinkRecycleBinRecoverReqDTO requestParam) {
        shortlinkRemoteService.recoverShortlink(requestParam);
        return Results.success();
    }

}
