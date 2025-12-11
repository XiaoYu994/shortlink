package com.xhy.shortlink.project.test;

import com.xhy.shortlink.project.service.Impl.ShortLinkServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShortLinkServiceTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations valueOperations;
    @Mock
    private HttpServletResponse response;
    // 【新增 1】Mock 布隆过滤器
    @Mock
    private RBloomFilter<String> shortlinkCachePenetrationBloomFilter;

    @InjectMocks
    private ShortLinkServiceImpl shortLinkService; // 你的实现类
    /*
    * 缓存存在但逻辑过期
    * */
    @Test
    void testCacheHit_ButExpiredLogically() throws IOException {
        // 1. 模拟 Redis 命中，但 Value 中的时间戳已过期 (昨天)
        long yesterday = System.currentTimeMillis() - 86400000L;
        String cacheValue = yesterday + "|https://www.yuque.com/";

        // Mock Redis 返回
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cacheValue);
        // 【新增 2】定义布隆过滤器的行为
        // 既然我们要测“缓存命中但逻辑过期”，说明这个链接必须在布隆过滤器里是存在的（返回 true）
        // 否则代码会在前面直接 return 404，走不到后面解析时间戳的逻辑
        when(shortlinkCachePenetrationBloomFilter.contains(anyString())).thenReturn(true);
        // 2. 调用方法
        shortLinkService.redirect("test", mock(HttpServletRequest.class), response);

        // 3. 验证
        // 验证是否重定向到了 404
        verify(response).sendRedirect("/page/notfound");
        // 验证是否删除了脏缓存
        verify(redisTemplate).delete(anyString());
    }
}
