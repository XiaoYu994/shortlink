package com.xhy.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.common.convention.result.Results;
import com.xhy.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkBaseInfoRespDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkBatchCreateRespDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.admin.toolkit.EasyExcelWebUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
* 后台调用短链接服务
* */
@RestController(value = "shortLinkControllerByAdmin")
@RequiredArgsConstructor
public class ShortLinkController {

   private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /*
     * 分页查询短链接
     * */
    @GetMapping("/api/short-link/admin/v1/page")
    public Result<Page<ShortLinkPageRespDTO>> pageShortlink(ShortLinkPageReqDTO requestParam) {
        return shortLinkActualRemoteService.pageShortlink(requestParam);
    }

    /*
     * 创建短链接
     * */
    @PostMapping("/api/short-link/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortlink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return shortLinkActualRemoteService.createShortlink(requestParam);
    }

    /*
     * 批量创建短链接
     */
    @PostMapping("/api/short-link/admin/v1/create/batch")
    public void batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam, HttpServletResponse response) {
        Result<ShortLinkBatchCreateRespDTO> shortLinkBatchCreateRespDTOResult = shortLinkActualRemoteService.batchCreateShortLink(requestParam);
        if (shortLinkBatchCreateRespDTOResult.isSuccess()) {
            List<ShortLinkBaseInfoRespDTO> baseLinkInfos = shortLinkBatchCreateRespDTOResult.getData().getBaseLinkInfos();
            EasyExcelWebUtil.write(response, "批量创建短链接-SaaS短链接系统", ShortLinkBaseInfoRespDTO.class, baseLinkInfos);
        }
    }

    /*
     * 修改短链接
     * */
    @PostMapping("/api/short-link/admin/v1/update")
    public Result<Void> updateShortlink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        shortLinkActualRemoteService.updateShortlink(requestParam);
        return Results.success();
    }
}
