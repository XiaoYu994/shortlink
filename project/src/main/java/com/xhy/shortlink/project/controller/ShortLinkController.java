package com.xhy.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.project.common.convention.result.Result;
import com.xhy.shortlink.project.common.convention.result.Results;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.project.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ShortLinkController {
    private final ShortLinkService shortlinkService;

    /*
    * 创建短链接
    * */
    @PostMapping("/api/short-link/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortlink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortlinkService.createShortlink(requestParam));
    }

    /*
    * 分页查询短链接
    * */
    @GetMapping("/api/short-link/v1/page")
    public Result<IPage<ShortLinkPageRespDTO> > pageShortlink(ShortLinkPageReqDTO requestParam) {
        return Results.success(shortlinkService.pageShortlink(requestParam));
    }

    /*
    * 查询gid下的短链接数量
    * */
    @GetMapping("api/short-link/v1/count")
    public Result<List<ShortLinkGroupCountRespDTO>> listGroupShortlinkCount(@RequestParam("requestParam")List<String> requestParam) {
        return Results.success(shortlinkService.listGroupShortlinkCount(requestParam));
    }
}
