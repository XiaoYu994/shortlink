package com.xhy.shortlink.admin.controller;

import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.common.convention.result.Results;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupAddReqDTO;
import com.xhy.shortlink.admin.dto.resp.ShortlinkGroupRespDTO;
import com.xhy.shortlink.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    /*
    * 新增短链接分组
    * */
    @PostMapping("/api/short-link/admin/v1/group")
    public Result<Void> addGroup(@RequestBody ShortlinkGroupAddReqDTO requestParam) {
        groupService.addGroup(requestParam);
        return Results.success();
    }

    /*
    * 查询短链接分组集合
    * */
    @GetMapping("/api/short-link/admin/v1/group")
    public Result<List<ShortlinkGroupRespDTO>> listGroup() {
        return Results.success(groupService.listGroup());
    }

}
