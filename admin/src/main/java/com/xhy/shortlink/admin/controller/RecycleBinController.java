package com.xhy.shortlink.admin.controller;

import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.common.convention.result.Results;
import com.xhy.shortlink.admin.remote.ShortLinkRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkRecycleBinSaveReqDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/*
* 后管调用中台回收站接口
* */
@RestController
public class RecycleBinController {
    /*
     * TODO 后续重构为SpringCloud Feign调用
     * */
    ShortLinkRemoteService shortlinkRemoteService = new ShortLinkRemoteService(){
    };

    @PostMapping("/api/short-link/admin/v1/recycle-bin/save")
    public Result<Void> recycleBinSave(@RequestBody ShortLinkRecycleBinSaveReqDTO requestParam) {
        shortlinkRemoteService.RecycleBinSave(requestParam);
        return Results.success();
    }
}
