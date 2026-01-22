package com.xhy.shortlink.project.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.xhy.shortlink.project.common.convention.result.Result;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;

/*
* 自定义流控策略
* */
@Slf4j
public class CustomBlockHandler {
    /*
    *  创建短链接接口限流降级策略
    * */
    public static Result<ShortLinkCreateRespDTO> createShortLinkBlockHandlerMethod(ShortLinkCreateReqDTO requestParam, BlockException exception) {
        return new Result<ShortLinkCreateRespDTO>().setCode("B100000").setMessage("当前访问网站人数过多，请稍后再试...");
    }

    /*
    *  短链接跳转降级策略
    * */
    public static void redirectBlockHandler(String shortUri, ServletRequest request, ServletResponse response, BlockException exception) {
        try {
            // 1. 关键修改：设置 Content-Type 为 text/html
            response.setContentType("text/html;charset=UTF-8");

            // 2. 编写一个简单的 HTML 页面
            // 这里包含了一些内联 CSS，让页面看起来居中、干净
            String html = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>系统繁忙</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        background-color: #f5f7fa;
                        color: #333;
                    }
                    .container {
                        text-align: center;
                        padding: 40px;
                        background: white;
                        border-radius: 12px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                        max-width: 90%;
                        width: 400px;
                    }
                    h1 { font-size: 24px; margin-bottom: 16px; color: #ff6b6b; }
                    p { color: #666; line-height: 1.6; margin-bottom: 24px; }
                    .btn {
                        display: inline-block;
                        padding: 10px 24px;
                        background-color: #409eff;
                        color: white;
                        text-decoration: none;
                        border-radius: 6px;
                        font-size: 14px;
                        transition: background 0.3s;
                        cursor: pointer;
                    }
                    .btn:hover { background-color: #66b1ff; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1> 访问人数过多</h1>
                    <p>当前短链接访问过于火爆，服务器正在全力处理中。<br>请稍等片刻后再尝试访问。</p>
                    <a href="javascript:location.reload();" class="btn">刷新重试</a>
                </div>
            </body>
            </html>
            """;

            // 3. 输出 HTML
            PrintWriter out = response.getWriter();
            out.write(html);
            out.flush();
        } catch (Exception e) {
            // 记录日志或忽略
            log.error("短链接跳转降级策略执行失败：{}", e.getMessage(), e);
        }
    }
}
