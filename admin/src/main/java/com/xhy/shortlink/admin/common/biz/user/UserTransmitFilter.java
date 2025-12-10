package com.xhy.shortlink.admin.common.biz.user;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

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
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest  httpServletRequest  = (HttpServletRequest)  servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;

        String requestURI = httpServletRequest.getRequestURI();
        if (IGNORE_URL.contains(requestURI)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String token = httpServletRequest.getHeader("token");
        String username = httpServletRequest.getHeader("username");

        // 1. 缺少必要身份标识 → 401
        if (!StringUtils.hasText(token) || !StringUtils.hasText(username)) {
            writeUnauthorizedResponse(httpServletResponse);
            return;
        }

        // 2. 查询 Redis
        String key = LOGIN_USER_KEY + username;
        Object userJson = stringRedisTemplate.opsForHash().get(key, token);

        if (userJson == null) {
            writeUnauthorizedResponse(httpServletResponse);
            return;
        }

        // 3. 保存到上下文
        UserContext.setUser(JSON.parseObject(userJson.toString(), UserInfoDTO.class));
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            UserContext.removeUser();
        }
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        try (PrintWriter out = response.getWriter()) {
            out.print(JSON.toJSONString(new ErrorResponse("401", "用户未登录或 token 不存在")));
            out.flush();
        }
    }
  @Data
 class ErrorResponse {
     private String code;
     private String message;

     public ErrorResponse(String code, String message) {
         this.code = code;
         this.message = message;
     }
 }
}