package com.xhy.shortlink.project.mq.consumer.RocketMQ;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dto.resp.ShortLinkRiskCheckRespDTO;
import com.xhy.shortlink.project.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.project.service.ShortLinkService;
import com.xhy.shortlink.project.service.UrlRiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;
import static com.xhy.shortlink.project.common.constant.RocketMQConstant.*;

@Slf4j
@Component
// 定义一个新的消费者组和 Topic
@RocketMQMessageListener(
        topic = RISK_CHECK_TOPIC,
        consumerGroup = RISK_CHECK_GROUP
)
@RequiredArgsConstructor
public class ShortLinkRiskRocketMQConsumer implements RocketMQListener<ShortLinkRiskEvent> {

    private final UrlRiskControlService riskControlService;
    private final ShortLinkService shortLinkService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate; // 用于发送广播清除本地缓存

    @Override
    public void onMessage(ShortLinkRiskEvent event) {

        log.info("开始对短链接进行 AI 风控审核: {}", event.getFullShortUrl());
        // 1. 调用 AI 进行检测
        ShortLinkRiskCheckRespDTO result = riskControlService.checkUrlRisk(event.getOriginUrl());

        // 2. 如果 AI 判定为不安全
        if (!result.isSafe()) {
            log.warn("⚠️ 发现违规链接！URL: {}, 类型: {}, 原因: {}",
                    event.getFullShortUrl(), result.getRiskType(), result.getDescription());
            // 3. 封禁处理
            disableLink(event);
        } else {
            log.info("✅ AI 审核通过: {}", event.getFullShortUrl());
        }
    }

    private void disableLink(ShortLinkRiskEvent event) {
        // A. 修改数据库状态 enable_status = 1 (禁用)
        shortLinkService.update(null, Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, event.getGid())
                .eq(ShortLinkDO::getFullShortUrl, event.getFullShortUrl())
                .set(ShortLinkDO::getEnableStatus, 1)); // 1 表示禁用

        // B. 删除 Redis 缓存
        String redisKey = String.format(GOTO_SHORT_LINK_KEY, event.getFullShortUrl());
        stringRedisTemplate.delete(redisKey);

        // C. 发送广播消息，清除所有节点的本地 Caffeine 缓存
        try {
            rocketMQTemplate.convertAndSend(CACHE_INVALIDATE_TOPIC, event.getFullShortUrl());
        } catch (Exception e) {
            log.error("风控封禁广播发送失败", e);
        }
    }
}