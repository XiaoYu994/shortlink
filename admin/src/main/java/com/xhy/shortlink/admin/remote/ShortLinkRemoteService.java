package com.xhy.shortlink.admin.remote;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* 短链接服务接口
 */
public interface ShortLinkRemoteService {


    /**
     * 创建短链接
     * @param requestParam 请求参数
     * @return 创建结果
     * */
    default Result<ShortLinkCreateRespDTO> createShortlink(ShortLinkCreateReqDTO requestParam) {
        final String resultBodyStr = HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/create", JSON.toJSONString(requestParam));
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }

    /**
     * 修改短链接
     * @param requestParam 请求参数
     * */
    default void updateShortlink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
       HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/update", JSON.toJSONString(requestParam));
    }


    /**
     * 分页查询短连接
     * @param requestParam 请求参数
     * @return 分页结果
     * */
    default Result<IPage<ShortLinkPageRespDTO>> pageShortlink(ShortLinkPageReqDTO requestParam) {
        Map<String, Object> result = new HashMap<>();
        result.put("gid", requestParam.getGid());
        result.put("current",requestParam.getCurrent());
        result.put("size",requestParam.getSize());
        final String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/page", result);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 分页查询短连接
     * @param requestParam 请求参数
     * @return 分页结果
     * */
    default Result<List<ShortLinkGroupCountRespDTO>> listGroupShortlinkCount(List<String> requestParam) {
        Map<String, Object> result = new HashMap<>();
        result.put("requestParam",requestParam);
        final String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/count", result);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }


   default Result<String> getPageTitle(String url){
       final String resultStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/title?url=" + url);
       return JSON.parseObject(resultStr, new TypeReference<>() {
       });
   }
}
