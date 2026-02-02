package com.xhy.shortlink.project.mq.consumer.redis;


import cn.hutool.core.date.DateUtil;
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
import com.xhy.shortlink.project.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.*;
import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;
import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.TODAY_EXPIRETIME;

/*
 * 短链接监控状态保存消息队列消费者 Redis Stream实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "short-link.message-queue.implement", havingValue = "Redis")
public class ShortLinkStatsSaveRedisConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    private final RedissonClient redissonClient;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MessageQueueIdempotentHandler  messageQueueIdempotentHandler;
    private final ShortLinkService shortLinkService;

    @Value("${short-link.cold-data.rehot.threshold:1000}")
    private int rehotThreshold;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        final Map<String, String> proudcerMap = message.getValue();
        // 1. 获取 Redis 生成的消息 ID (用于最后删除)
        RecordId id = message.getId();
        final ShortLinkStatsRecordEvent statsRecord = JSON.parseObject(proudcerMap.get("json"), ShortLinkStatsRecordEvent.class);
        final String stream = message.getStream();
        // 2. 获取业务 UUID (用于幂等性判断)
        final String messageId = statsRecord.getEventId();

        try {
            // 如果被消费
            if(messageQueueIdempotentHandler.isMessageBeingConsumed(messageId)) {
                // 消息执行完成
                if(messageQueueIdempotentHandler.isAccomplish(messageId)) {
                    return;
                }
                throw new ServiceException("消息未完成流程，需要消息队列重试");
            }
            actualSaveShortLinkStats(statsRecord);
            stringRedisTemplate.opsForStream().delete(Objects.requireNonNull(stream),id.getValue());
            log.info("[Redis-Stream] 消费监控统计消息 ,{}", messageId);
        } catch (Throwable e){
            // 某某某情况宕机了
            messageQueueIdempotentHandler.delMessageProcessed(messageId);
            log.error("消息消费失败", e);
            throw e;
        }
        // 设置消息流程执行完成
        messageQueueIdempotentHandler.setAccomplish(messageId);

    }

    private void actualSaveShortLinkStats(ShortLinkStatsRecordEvent statsRecord) {
        String fullShortUrl = statsRecord.getFullShortUrl();
        // 运行并发写入统计，修改互斥
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();
        rLock.lock();
        try {
            // 1. 获取 GID
            String gid = statsRecord.getGid();
            if (StrUtil.isBlank(gid)) {
                LambdaQueryWrapper<ShortLinkGoToDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                        .eq(ShortLinkGoToDO::getFullShortUrl, fullShortUrl);
                ShortLinkGoToDO shortLinkGotoDO = shortLinkGoToMapper.selectOne(queryWrapper);
                gid = shortLinkGotoDO.getGid();
            }

            // 2. 使用消息体的时间，保证 redis 和 DB 一致
            String todayStr = DateUtil.formatDate(statsRecord.getCurrentDate());

            // 3. Redis 统计
            // A. PV
            String pvRankKey = String.format(RANK_KEY, OrderTagEnum.TODAY_PV.getValue(), gid, todayStr);
            stringRedisTemplate.opsForZSet().incrementScore(pvRankKey, fullShortUrl, 1);

            // B. 今日 UV Key (带日期) -> 用于判断“今日是否新用户”
            String todayUvHllKey = String.format(TODAY_UV_HLL_KEY,fullShortUrl,todayStr);
            // uvAddedToday = 1 (新) / 0 (旧)
            Long uvAddedToday = stringRedisTemplate.opsForHyperLogLog().add(todayUvHllKey, statsRecord.getUv());
            // 为 uv 添加过期时间
            if (stringRedisTemplate.getExpire(todayUvHllKey) == -1) {
                stringRedisTemplate.expire(todayUvHllKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }

            long todayUvCount = stringRedisTemplate.opsForHyperLogLog().size(todayUvHllKey);
            String uvRankKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UV.getValue(), gid, todayStr);
            stringRedisTemplate.opsForZSet().add(uvRankKey, fullShortUrl, todayUvCount);

            // C.历史总 UV Key (不带日期) -> 用于判断“历史上是否新用户”
            String totalUvHllKey = String.format(TOTAL_UV_HLL_KEY,fullShortUrl);
            Long uvAddedTotal = stringRedisTemplate.opsForHyperLogLog().add(totalUvHllKey, statsRecord.getUv());

            // D. UIP
            String todayUipHllKey = String.format(TODAY_UIP_HLL_KEY,fullShortUrl,todayStr);
            Long uipAddedToday = stringRedisTemplate.opsForHyperLogLog().add(todayUipHllKey, statsRecord.getRemoteAddr());
            if (stringRedisTemplate.getExpire(todayUipHllKey) == -1) {
                stringRedisTemplate.expire(todayUipHllKey, TODAY_EXPIRETIME, TimeUnit.HOURS);
            }

            long todayUipCount = stringRedisTemplate.opsForHyperLogLog().size(todayUipHllKey);
            String uipRankKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UIP.getValue(), gid, todayStr);
            stringRedisTemplate.opsForZSet().add(uipRankKey, fullShortUrl, todayUipCount);

            // E. Total UIP
            String totalUipHllKey = String.format(TOTAL_UIP_HLL_KEY,fullShortUrl);
            Long uipAddedTotal = stringRedisTemplate.opsForHyperLogLog().add(totalUipHllKey, statsRecord.getRemoteAddr());

            // 4. 数据库统计 (PV 都在这里 +1)
            saveStatsToDatabase(
                    statsRecord,
                    fullShortUrl,
                    gid,
                    uvAddedToday == 1,
                    uipAddedToday == 1,
                    uvAddedTotal == 1,
                    uipAddedTotal == 1
            );
        } finally {
            rLock.unlock();
        }
    }

    /**
     * 将详细的访问统计数据保存到数据库
     * 包括：基础统计、地区、OS、浏览器、设备、网络、详细日志
     */
    private void saveStatsToDatabase(ShortLinkStatsRecordEvent statsRecord,
                                     String fullShortUrl,
                                     String gid,
                                     boolean isTodayNewUv,
                                     boolean isTodayNewUip,
                                     boolean isTotalNewUv,
                                     boolean isTotalNewUip) {
        Date currentDate = statsRecord.getCurrentDate();
        String todayStr = DateUtil.formatDate(currentDate);
        // 提取时间维度
        int hour = DateUtil.hour(currentDate, true);
        int weekday = DateUtil.dayOfWeekEnum(currentDate).getIso8601Value();
        String remoteAddr = statsRecord.getRemoteAddr();

        // 1. 远程调用高德接口解析 IP (增加超时保护)
        String actualProvince = "未知";
        String actualCity = "未知";
        String adcode = "未知";

        Map<String, Object> localeParamMap = new HashMap<>();
        localeParamMap.put("key", statsLocaleAmapKey);
        localeParamMap.put("ip", remoteAddr);

        try {
            // 设置 3000ms 超时，防止 HTTP 请求阻塞消费者线程
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap, 3000);
            JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            if ("10000".equals(localeResultObj.getString("infocode"))) {
                String province = localeResultObj.getString("province");
                boolean unknownProvince = StrUtil.isBlank(province) || "[]".equals(province);
                actualProvince = unknownProvince ? actualProvince : province;

                String city = localeResultObj.getString("city");
                boolean unknownCity = StrUtil.isBlank(city) || "[]".equals(city);
                actualCity = unknownCity ? actualCity : city;

                String code = localeResultObj.getString("adcode");
                boolean unknownAdcode = StrUtil.isBlank(code) || "[]".equals(code);
                adcode = unknownAdcode ? adcode : code;
            }
        } catch (Exception e) {
            log.warn("IP解析失败或超时, IP: {}", remoteAddr, e);
            // 异常时保持默认值“未知”，不阻断流程
        }

        // 2. 保存地区统计 (LinkLocaleStats)
        // 假设 Mapper XML 中使用了 INSERT ... ON DUPLICATE KEY UPDATE cnt = cnt + 1
        LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                .fullShortUrl(fullShortUrl)
                .province(actualProvince)
                .city(actualCity)
                .adcode(adcode)
                .cnt(1)
                .country("中国")
                .date(currentDate)
                .build();
        linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);

        // 3. 保存操作系统统计 (LinkOsStats)
        LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                .fullShortUrl(fullShortUrl)
                .os(statsRecord.getOs())
                .cnt(1)
                .date(currentDate)
                .build();
        linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);

        // 4. 保存浏览器统计 (LinkBrowserStats)
        LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                .fullShortUrl(fullShortUrl)
                .browser(statsRecord.getBrowser())
                .cnt(1)
                .date(currentDate)
                .build();
        linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);

        // 5. 保存设备统计 (LinkDeviceStats)
        LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                .fullShortUrl(fullShortUrl)
                .device(statsRecord.getDevice())
                .cnt(1)
                .date(currentDate)
                .build();
        linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);

        // 6. 保存网络统计 (LinkNetworkStats)
        LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                .fullShortUrl(fullShortUrl)
                .network(statsRecord.getNetwork())
                .cnt(1)
                .date(currentDate)
                .build();
        linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);

        // 7. 保存访问流水日志 (LinkAccessLogs) - 纯新增，不聚合
        LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                .fullShortUrl(fullShortUrl)
                .ip(remoteAddr)
                .user(statsRecord.getUv()) // Cookie UUID
                .os(statsRecord.getOs())
                .browser(statsRecord.getBrowser())
                .device(statsRecord.getDevice())
                .network(statsRecord.getNetwork())
                .locale(StrUtil.join("-", "中国", actualProvince, actualCity))
                .build();
        linkAccessLogsMapper.insert(linkAccessLogsDO);

        // 8. 保存基础访问统计 (LinkAccessStats) - 分小时统计 PV/UV/UIP
        // 注意：因为使用了 HLL，这里的 UV/UIP 仅仅是记录 MQ 传过来的标记
        // 如果要严格一致，数据库的 UV/UIP 统计建议依赖 HLL 的结果或 AccessLogs 的离线分析
        LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                .fullShortUrl(fullShortUrl)
                .date(currentDate)
                .hour(hour)
                .weekday(weekday)
                .pv(1) // PV 永远 +1
                .uv(isTodayNewUv ? 1 : 0)
                .uip(isTodayNewUip ? 1 : 0)
                .build();
        linkAccessStatsMapper.shortLinkStats(Collections.singletonList(linkAccessStatsDO));

        // 9. 主表总数统计 (ShortLinkMapper)
        // 更新 total_pv, total_uv, total_uip
        // 这里利用 HLL 对“历史总数”的判断
        int affected = shortLinkMapper.incrementStats(
                gid,
                fullShortUrl,
                1, // total_pv +1
                isTotalNewUv ? 1 : 0,
                isTotalNewUip ? 1 : 0
        );
        if (affected == 0) {
            shortLinkColdMapper.incrementStats(
                    gid,
                    fullShortUrl,
                    1,
                    isTotalNewUv ? 1 : 0,
                    isTotalNewUip ? 1 : 0
            );
        }
        tryRehotIfNeeded(fullShortUrl, gid, todayStr);
    }

    /**
     * 统计链路回热：当今日PV达到阈值，触发冷库回热
     */
    private void tryRehotIfNeeded(String fullShortUrl, String gid, String todayStr) {
        if (rehotThreshold <= 0) {
            return;
        }
        String pvKey = String.format(RANK_KEY, OrderTagEnum.TODAY_PV.getValue(), gid, todayStr);
        Double pvScore = stringRedisTemplate.opsForZSet().score(pvKey, fullShortUrl);
        long pv = pvScore == null ? 0L : pvScore.longValue();
        if (pv >= rehotThreshold) {
            shortLinkService.rehotColdLink(fullShortUrl, gid);
        }
    }
}
