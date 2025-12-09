package com.xhy.shortlink.admin.common.biz.user;

import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.admin.common.convention.exception.ClientException;
import com.xhy.shortlink.admin.common.enums.UserErrorCodeEnum;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static com.xhy.shortlink.admin.common.constant.RedisCacheConstant.LOGIN_USER_KEY;

/*
 * 用户信息传输过滤器
 *
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {
    private final StringRedisTemplate stringRedisTemplate;

    private static final List<String> IGNORE_URL = List.of("/api/short-link/admin/v1/user/login",
            "/api/short-link/admin/v1/user/check-login","/api/short-link/admin/v1/user/has-username");

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        final String requestURI = httpServletRequest.getRequestURI();
        // 忽略的URL
        if(IGNORE_URL.contains(requestURI)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String token = httpServletRequest.getHeader("token");
        String username = httpServletRequest.getHeader("username");
        final Object userInfoJsonStr = stringRedisTemplate.opsForHash().get(LOGIN_USER_KEY + username, token);
        try {
            if (userInfoJsonStr == null) {
                // 抛出自定义异常 这里不会被全局异常处理器捕获到
                throw new ClientException(UserErrorCodeEnum.USER_LOGIN_ERROR);
            }

            UserInfoDTO userInfoDTO = JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
            UserContext.setUser(userInfoDTO);

            filterChain.doFilter(servletRequest, servletResponse);
        } catch (ClientException e) {
            // 设置错误响应
            httpServletResponse.setContentType("application/json;charset=UTF-8");
            httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            PrintWriter out = httpServletResponse.getWriter();
            out.print(JSON.toJSONString(new ErrorResponse("401", "用户未登录或者token不存在")));
            out.flush();
            out.close();
        } finally {
            UserContext.removeUser();
        }
    }
}

 class ErrorResponse {
    private String code;
    private String message;

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}