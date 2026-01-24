package com.xhy.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.common.convention.result.Results;
import com.xhy.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkRecycleBinPageRespDTO;
import com.xhy.shortlink.admin.service.RecycleBinService;
import com.xhy.shortlink.admin.toolkit.ResultUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/*
* 后管调用中台回收站接口
* */
@RestController(value = "recycleBinControllerByAdmin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final RecycleBinService recycleBinService;

    /*
    * 后管调用中台短链接回收接口
    * */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/save")
    public Result<Void> recycleBinSave(@RequestBody ShortLinkRecycleBinSaveReqDTO requestParam) {
        ResultUtils.check(shortLinkActualRemoteService.RecycleBinSave(requestParam));
        return Results.success();
    }

    /*
    * 后管调用中台回收链接分页查询接口
    * */
    @GetMapping("/api/short-link/admin/v1/recycle-bin/page")
    public Result<Page<ShortLinkRecycleBinPageRespDTO>> pageRecycleShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return recycleBinService.pageRecycleShortlink(requestParam);
    }

    /*
     * 后管调用中台恢复回收站链接接口
     * */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/recover")
    public Result<Void> recoverShortlink(@RequestBody ShortLinkRecycleBinRecoverReqDTO requestParam) {
        ResultUtils.check(shortLinkActualRemoteService.recoverShortlink(requestParam));
        return Results.success();
    }

    /*
     * 后管调用中台删除回收站链接接口
     * */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/remove")
    public Result<Void> removeShortlink(@RequestBody ShortLinkRecycleBinRemoveReqDTO requestParam) {
        ResultUtils.check(shortLinkActualRemoteService.removeShortlink(requestParam));
        return Results.success();
    }


}
