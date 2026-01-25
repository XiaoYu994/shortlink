package com.xhy.shortlink.admin.remote;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.config.OpenFeignConfiguration;
import com.xhy.shortlink.admin.remote.dto.req.*;
import com.xhy.shortlink.admin.remote.dto.resp.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * OpenFeign 远程调用服务
 */
@FeignClient(
        value = "short-link-project",
        url = "${aggregation.remote-url:}",
        configuration = OpenFeignConfiguration.class
)
public interface ShortLinkActualRemoteService {

    /**
     * 创建短链接
     * @param requestParam 请求参数
     * @return 创建结果
     * */
     @PostMapping("/api/short-link/v1/create")
     Result<ShortLinkCreateRespDTO> createShortlink(@RequestBody ShortLinkCreateReqDTO requestParam);

    /**
     * 批量创建短链接
     * @param requestParam 请求参数
     * @return 创建结果
     * */
     @PostMapping("/api/short-link/v1/create/batch")
     Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam);


    /**
     * 修改短链接
     * @param requestParam 请求参数
     * */
     @PostMapping("/api/short-link/v1/update")
     Result<Void> updateShortlink(@RequestBody ShortLinkUpdateReqDTO requestParam);


    /**
     * 分页查询短连接
     * @param requestParam 请求参数
     * @return 分页结果
     * */
     @GetMapping("/api/short-link/v1/page")
     Result<Page<ShortLinkPageRespDTO>> pageShortlink(@SpringQueryMap ShortLinkPageReqDTO requestParam);

    /**
     * 查询分组下短连接数量
     * @param requestParam 请求参数
     * @return 返回结果
     * */
     @GetMapping("/api/short-link/v1/count")
     Result<List<ShortLinkGroupCountRespDTO>> listGroupShortlinkCount(@RequestParam("requestParam") List<String> requestParam);

    /**
     * 获取目标网站标题
     * @param url 目标网站
     * @return 标题
     * */
     @GetMapping("/api/short-link/v1/title")
     Result<String> getPageTitle(@RequestParam("url") String url);

    /**
     * 移动到回收站
     * @param requestParam 回收站请求参数
     */
    @PostMapping("/api/short-link/v1/recycle-bin/save")
    Result<Void> RecycleBinSave(@RequestBody ShortLinkRecycleBinSaveReqDTO requestParam);

    /**
     * 分页查询回收站链接
     * @param requestParam 请求参数
     * @return 分页结果
     */
    @GetMapping("/api/short-link/v1/recycle-bin/page")
    Result<Page<ShortLinkPageRespDTO>> pageRecycleShortlink(@SpringQueryMap ShortLinkRecycleBinPageReqDTO requestParam);

    /**
     * 恢复短链接
     * @param requestParam 请求参数
     */
    @PostMapping("/api/short-link/v1/recycle-bin/recover")
    Result<Void> recoverShortlink(@RequestBody ShortLinkRecycleBinRecoverReqDTO requestParam);

    /**
     * 移除短链接
     * @param requestParam 请求参数
     */
    @PostMapping("/api/short-link/v1/recycle-bin/remove")
    Result<Void> removeShortlink(@RequestBody ShortLinkRecycleBinRemoveReqDTO requestParam);

    /**
     * 获取单个短链接监控数据
     *
     * @param requestParam 获取短链接监控数据入参
     * @return 短链接监控数据
     */
    @GetMapping("/api/short-link/v1/stats")
    Result<ShortLinkStatsRespDTO> oneShortLinkStats(@SpringQueryMap ShortLinkStatsReqDTO requestParam);

    /**
     * 获取分组短链接监控数据
     *
     * @param requestParam 获取短链接监控数据分组入参
     * @return 短链接监控数据
     */
    @GetMapping("/api/short-link/v1/stats/group")
    Result<ShortLinkStatsRespDTO> groupShortLinkStats(@SpringQueryMap ShortLinkStatsGroupReqDTO requestParam);

    /**
     * 获取单个短链接日志监控数据
     *
     * @param requestParam 获取短链接监控日志数据入参
     * @return 短链接监控日志数据
     */
    @GetMapping("/api/short-link/v1/stats/access-record")
    Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(@SpringQueryMap ShortLinkStatsAccessRecordReqDTO requestParam);
    /**
     * 获取分组短链接日志监控数据
     *
     * @param requestParam 获取短链接监控日志数据入参
     * @return 短链接监控日志数据
     */
    @GetMapping("/api/short-link/v1/stats/access-record/group")
    Result<Page<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(@SpringQueryMap ShortLinkStatsAccessRecordGroupReqDTO requestParam);
}
