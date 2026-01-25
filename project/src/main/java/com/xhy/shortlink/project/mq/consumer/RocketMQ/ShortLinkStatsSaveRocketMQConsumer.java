package com.xhy.shortlink.project.mq.consumer.RocketMQ;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.common.enums.OrderTagEnum;
import com.xhy.shortlink.project.dao.entity.*;
import com.xhy.shortlink.project.dao.mapper.*;
import com.xhy.shortlink.project.handler.MessageQueueIdempotentHandler;
import com.xhy.shortlink.project.mq.event.ShortLinkStatsRecordEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.LOCK_GID_UPDATE_KEY;
import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.RANK_KEY;
import static com.xhy.shortlink.project.common.constant.RocketMQConstant.STATIC_GROUP;
import static com.xhy.shortlink.project.common.constant.RocketMQConstant.STATIC_TOPIC;
import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;
import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.TODAY_EXPIRETIME;

/*
 * 短链接监控状态保存消息队列消费者 RocketMQ实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "RocketMQ")
@RocketMQMessageListener(
        topic = STATIC_TOPIC,
        consumerGroup = STATIC_GROUP
)
public class ShortLinkStatsSaveRocketMQConsumer implements RocketMQListener<ShortLinkStatsRecordEvent> {
    private final RedissonClient redissonClient;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;
    // 定义 Lua 脚本对象
    private final DefaultRedisScript<Long> statsSaveScript;
    private final StringRedisTemplate stringRedisTemplate;
    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    @Override
    public void onMessage(ShortLinkStatsRecordEvent event) {
        String keys = event.getEventId();
        if (messageQueueIdempotentHandler.isMessageBeingConsumed(keys)) {
            // 判断当前的这个消息流程是否执行完成
            if (messageQueueIdempotentHandler.isAccomplish(keys)) {
                return;
            }
            throw new ServiceException("消息未完成流程，需要消息队列重试");
        }
        try {
            actualSaveShortLinkStats(event);
        } catch (Throwable ex) {
            // 删除幂等标识
            messageQueueIdempotentHandler.delMessageProcessed(keys);
            log.error("记录短链接监控消费异常", ex);
            throw ex;
        }
        messageQueueIdempotentHandler.setAccomplish(keys);
    }

    private void actualSaveShortLinkStats(ShortLinkStatsRecordEvent statsRecord) {
        String fullShortUrl = statsRecord.getFullShortUrl();
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();
        rLock.lock();
        try {
            // 只有当gid为空的时候才去查询路由表
            LambdaQueryWrapper<ShortLinkGoToDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                    .eq(ShortLinkGoToDO::getFullShortUrl, fullShortUrl);
            ShortLinkGoToDO shortLinkGotoDO = shortLinkGoToMapper.selectOne(queryWrapper);
            String gid = shortLinkGotoDO.getGid();
            Date currentDate = statsRecord.getCurrentDate();
            int hour = DateUtil.hour(currentDate, true);
            Week week = DateUtil.dayOfWeekEnum(currentDate);
            int weekday = week.getIso8601Value();
            // 构造基础访问统计数据
            final LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .hour(hour)
                    .weekday(weekday)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .pv(1)
                    .uv(statsRecord.getUvFirstFlag() ? 1:0)
                    .uip(statsRecord.getUipFirstFlag() ? 1:0)
                    .build();
            linkAccessStatsMapper.shortLinkStats(List.of(linkAccessStatsDO));
            // 构造区域访问统计数据
            Map<String,Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key",statsLocaleAmapKey);
            localeParamMap.put("ip",statsRecord.getRemoteAddr());
            // 远程调用百度接口
            // 根据高德接口返回结果 TODO 有问题目前
            String actualProvince = "未知";
            String actualCity = "未知";
            String adcode = "未知";
            try {
                String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
                JSONObject localeResultObj = JSON.parseObject(localeResultStr);
                if ("10000".equals(localeResultObj.getString("infocode"))) {
                    actualProvince = localeResultObj.getString("province").equals("[]") ? actualProvince : localeResultObj.getString("province");
                    actualCity = localeResultObj.getString("city").equals("[]") ? actualCity : localeResultObj.getString("city");
                    adcode =  localeResultObj.getString("adcode").equals("[]") ? adcode : localeResultObj.getString("adcode");
                }
            } catch (Exception e) {
                log.warn("IP解析失败，使用默认值", e);
            }
            final LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                    .province(actualProvince)
                    .city(actualCity )
                    .adcode(adcode)
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .country("中国")
                    .date(currentDate)
                    .build();
            linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
            // 构造操作系统访问统计数据
            final LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .os(statsRecord.getOs())
                    .cnt(1)
                    .build();
            linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);
            // 构造浏览器访问统计数据
            final LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                    .browser(statsRecord.getBrowser())
                    .date(currentDate)
                    .fullShortUrl(fullShortUrl)
                    .cnt(1)
                    .build();
            linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);
            // 设备访问数据统计
            final LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .device(statsRecord.getDevice())
                    .cnt(1)
                    .build();
            linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);
            // 获取短链接访问网络统计数据
            final LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                    .network(statsRecord.getNetwork())
                    .date(currentDate)
                    .fullShortUrl(fullShortUrl)
                    .cnt(1)
                    .build();
            linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);
            // 构造短链接日志统计数据
            final LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                    .ip(statsRecord.getRemoteAddr())
                    .user(statsRecord.getUv())
                    .os(statsRecord.getOs())
                    .browser(statsRecord.getBrowser())
                    .fullShortUrl(fullShortUrl)
                    .device(statsRecord.getDevice())
                    .network(statsRecord.getNetwork())
                    .locale(StrUtil.join("-", "中国", actualProvince, actualCity))
                    .build();
            linkAccessLogsMapper.insert(linkAccessLogsDO);
            // 短链接访问统计数据自增
            shortLinkMapper.incrementStats(gid, fullShortUrl,1,statsRecord.getUvFirstFlag() ? 1:0, statsRecord.getUipFirstFlag() ? 1:0);
            // --- 开始 Redis 写入优化 ---
            String todayDate = DateUtil.formatDate(new Date());
            // 构造 3 个 Key
            String pvKey = String.format(RANK_KEY, OrderTagEnum.TODAY_PV.getValue(), gid, todayDate);
            String uvKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UV.getValue(),gid, todayDate);
            String uipKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UIP.getValue(),gid, todayDate);

            // 构造参数
            List<String> keys = Arrays.asList(pvKey, uvKey, uipKey);
            String uvFlag = statsRecord.getUvFirstFlag() ? "true" : "false";
            String uipFlag = statsRecord.getUipFirstFlag() ? "true" : "false";
            // 后续可以进行本地缓存缓冲 批量写入
            String score = "1";
            try {
                stringRedisTemplate.execute(
                        statsSaveScript,
                        keys,
                        fullShortUrl,
                        uvFlag,
                        uipFlag,
                        score,
                        TODAY_EXPIRETIME
                );
            } catch (Throwable ex) {
                // Redis 写入失败不应该影响主流程（MySQL已入库），打个日志即可
                // 或者根据业务严谨度决定是否抛出异常重试
                log.error("Redis排行榜数据写入失败", ex);
            }
        } finally {
            rLock.unlock();
        }

    }
}
