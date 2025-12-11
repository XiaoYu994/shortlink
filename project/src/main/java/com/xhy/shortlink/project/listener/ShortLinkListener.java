package com.xhy.shortlink.project.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.event.UpdateFaviconEvent;
import com.xhy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.project.toolkit.FaviconService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkListener {

    private final FaviconService faviconService; // 注入服务
    private final ShortLinkMapper shortLinkMapper;

    /**
     * 监听图标更新事件
     * 这里的逻辑完全独立于主线程，不会阻塞用户请求
     */
    @EventListener
    public void onUpdateFaviconEvent(UpdateFaviconEvent event) {
        log.info("监听到图标更新请求: {}", event.getOriginUrl());
        // 调用异步服务获取图标
        // 因为 getFaviconUrl 返回的是 CompletableFuture，我们使用 .thenAccept 处理结果
        faviconService.getFaviconUrl(event.getOriginUrl()).thenAccept(faviconUrl -> {
            // --- 这里是回调逻辑，图标获取成功后执行 ---
            // 构建只更新 favicon 的实体对象
            ShortLinkDO updateDO = ShortLinkDO.builder()
                    .favicon(faviconUrl)
                    .build();

            // 执行数据库更新
            shortLinkMapper.update(updateDO, Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, event.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, event.getGid()));

            log.info("图标更新成功，URL: {}, Icon: {}", event.getFullShortUrl(), faviconUrl);

        }).exceptionally(ex -> {
            log.error("图标更新过程中发生异常", ex);
            return null;
        });
    }
}