/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xhy.shortlink.biz.gatewayservice.filter;

import com.alibaba.fastjson2.JSON;
import com.xhy.shortlink.biz.gatewayservice.config.FlowLimitProperties;
import com.xhy.shortlink.biz.gatewayservice.dto.GatewayErrorResult;
import com.xhy.shortlink.framework.starter.cache.toolkit.RedisIncrWithExpire;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 注册、登录、创建短链的滑动窗口限流
 *
 * @author XiaoYu
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(FlowLimitProperties.class)
@ConditionalOnProperty(prefix = "short-link.flow-limit", name = "enable", havingValue = "true", matchIfMissing = true)
public class FlowLimitGatewayFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/short-link/admin/v1/user",
            "/api/short-link/admin/v1/user/login");

    private static final List<PathMatcher> LIMITED_PATHS = List.of(
            new PathMatcher("POST", "/api/short-link/admin/v1/user"),
            new PathMatcher("POST", "/api/short-link/admin/v1/user/login"),
            new PathMatcher("POST", "/api/short-link/admin/v1/create"),
            new PathMatcher("POST", "/api/short-link/admin/v1/create/batch"),
            new PathMatcher("POST", "/api/short-link/v1/create"),
            new PathMatcher("POST", "/api/short-link/v1/create/batch")
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final FlowLimitProperties flowLimitProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() == null ? "" : request.getMethod().name();
        if (!isLimited(method, path)) {
            return chain.filter(exchange);
        }
        String identity = resolveIdentity(request, path);
        String key = "short-link:flow-limit:gw:" + identity + ":" + path;
        return Mono.fromCallable(() -> incrementWithExpire(key))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(current -> {
                    if (current == null) {
                        return handleStoreFailure(exchange, chain);
                    }
                    if (current > flowLimitProperties.getMaxAccessCount()) {
                        return writeTooManyRequests(exchange);
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(error -> handleStoreFailure(exchange, chain));
    }

    @Override
    public int getOrder() {
        // TokenValidate 作为路由过滤器默认 order=0，须在其后读取注入的 username。
        return 1;
    }

    private Long incrementWithExpire(String key) {
        return RedisIncrWithExpire.increment(stringRedisTemplate, key, flowLimitProperties.getTimeWindow());
    }

    private Mono<Void> writeTooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return response.writeWith(Mono.fromSupplier(() -> {
            DataBufferFactory bufferFactory = response.bufferFactory();
            GatewayErrorResult resultMessage = GatewayErrorResult.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .message("访问过于频繁，请稍后再试")
                    .build();
            return bufferFactory.wrap(JSON.toJSONString(resultMessage).getBytes());
        }));
    }

    private boolean isLimited(String method, String path) {
        return LIMITED_PATHS.stream().anyMatch(matcher -> matcher.matches(method, path));
    }

    private String resolveIdentity(ServerHttpRequest request, String path) {
        if (!PUBLIC_AUTH_PATHS.contains(path)) {
            String username = request.getHeaders().getFirst("username");
            if (StringUtils.hasText(username)) {
                try {
                    return URLDecoder.decode(username, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ignored) {
                    // 非法编码的身份头不能阻断网关请求，退回可信代理地址。
                }
            }
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "anonymous";
    }

    private Mono<Void> handleStoreFailure(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (Boolean.TRUE.equals(flowLimitProperties.getFailOpen())) {
            return chain.filter(exchange);
        }
        return writeServiceUnavailable(exchange);
    }

    private Mono<Void> writeServiceUnavailable(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return response.writeWith(Mono.fromSupplier(() -> response.bufferFactory()
                .wrap(JSON.toJSONString(GatewayErrorResult.builder()
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .message("限流服务暂不可用，请稍后再试")
                        .build()).getBytes())));
    }

    private record PathMatcher(String method, String path) {
        boolean matches(String requestMethod, String requestPath) {
            return method.equalsIgnoreCase(requestMethod) && path.equals(requestPath);
        }
    }
}
