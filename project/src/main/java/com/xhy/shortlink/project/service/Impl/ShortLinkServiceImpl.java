package com.xhy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.common.convention.exception.ClientException;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.common.enums.ValidDateTypeEnum;
import com.xhy.shortlink.project.dao.entity.*;
import com.xhy.shortlink.project.dao.event.UpdateFaviconEvent;
import com.xhy.shortlink.project.dao.mapper.*;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.project.service.ShortLinkService;
import com.xhy.shortlink.project.toolkit.HashUtil;
import com.xhy.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.*;
import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

/*
* 短链接接口实现层
* */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {
    private final RBloomFilter<String> shortlinkCachePenetrationBloomFilter;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    // 1. 注入事件发布器
    private final ApplicationEventPublisher eventPublisher;
    // 监空统计有关mapper
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    @SneakyThrows
    @Override
    public void redirect(String shortUri, ServletRequest request, ServletResponse response) {
        // 不带http
        String fullShortUrl = request.getServerName() + "/" + shortUri;
        // 缓存 key
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        // 缓存空值key
        String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);

        // 1. 优先从缓存中查询
        String originalLinkComposite = stringRedisTemplate.opsForValue().get(key);

        // 【修正点1】如果缓存中有值，必须先解析，再判断是否过期/续期，最后跳转
        if (StrUtil.isNotBlank(originalLinkComposite)) {
            String redirectUrl = extractUrlAndRenew(originalLinkComposite, key);
            if (redirectUrl != null) {
                stats(fullShortUrl, request,response);
                ((HttpServletResponse) response).sendRedirect(redirectUrl);
                return;
            }
            // 如果返回 null，说明逻辑已过期或数据异常，视为缓存未命中，继续往下走（或者直接走回源逻辑）
            // 这里为了稳健，如果数据异常通常应该删缓存并重新查库，继续往下执行
            stringRedisTemplate.delete(key);
        }

        // 2. 布隆过滤器拦截 (解决缓存穿透)
        if (!shortlinkCachePenetrationBloomFilter.contains(fullShortUrl)) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }

        // 3. 查询空值缓存 (解决缓存穿透)
        String gotoIsNullShortLink = stringRedisTemplate.opsForValue().get(keyIsNull);
        if (StrUtil.isNotBlank(gotoIsNullShortLink)) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }

        // 4. 加锁 (解决缓存击穿)
        final RLock lock = redissonClient.getLock(LOOK_GOTO_SHORT_LINK_KEY);
        lock.lock();
        try {
            // 5. 双重检查 (Double Check)
            originalLinkComposite = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(originalLinkComposite)) {
                // 锁内的解析和续期（防止在等待锁的过程中别人已经查好放入缓存了）
                String redirectUrl = extractUrlAndRenew(originalLinkComposite, key);
                if (redirectUrl != null) {
                    stats(fullShortUrl, request,response);

                    ((HttpServletResponse) response).sendRedirect(redirectUrl);
                    return;
                }
                // 如果返回 null，说明逻辑已过期或数据异常，视为缓存未命中
                stringRedisTemplate.delete(key);
            }

            // 再次查空值缓存（严谨的双重检查）
            gotoIsNullShortLink = stringRedisTemplate.opsForValue().get(keyIsNull);
            if (StrUtil.isNotBlank(gotoIsNullShortLink)) {
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }

            // 6. 查询数据库 (回源)
            // 6.1 查路由表
            LambdaQueryWrapper<ShortLinkGoToDO> linkGoToWrapper = Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                    .eq(ShortLinkGoToDO::getFullShortUrl, fullShortUrl);
            ShortLinkGoToDO shortLinkGoToDO = shortLinkGoToMapper.selectOne(linkGoToWrapper);

            if (shortLinkGoToDO == null) {
                stringRedisTemplate.opsForValue().set(keyIsNull, "-", 30, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }

            // 6.2 查详情表
            LambdaQueryWrapper<ShortLinkDO> linkWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getGid, shortLinkGoToDO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, 0);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(linkWrapper);

            if (shortLinkDO == null || (shortLinkDO.getValidDate() != null && shortLinkDO.getValidDate().before(new Date()))) {
                stringRedisTemplate.opsForValue().set(keyIsNull, "-", 30, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }

            // 7. 重建缓存
            long validTimeStamp = (shortLinkDO.getValidDate() != null) ? shortLinkDO.getValidDate().getTime() : -1;
            String cacheValue = validTimeStamp + "|" + shortLinkDO.getOriginUrl();
            // 计算初始 TTL (例如：过期时间 - 当前时间，或者默认 1 天)
            long initialTTL = LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate());
            stringRedisTemplate.opsForValue().set(key, cacheValue, initialTTL, TimeUnit.MILLISECONDS);
            stats(fullShortUrl, request,response);
            ((HttpServletResponse) response).sendRedirect(shortLinkDO.getOriginUrl());
        } finally {
            lock.unlock();
        }
    }

    private void stats(String fullShortUrl,ServletRequest request, ServletResponse response) {
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
         Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        try {
            Runnable addResponseCookieTask = () -> {
                String uv =UUID.fastUUID().toString();
                final Cookie cookie = new Cookie("uv", uv);
                cookie.setMaxAge(60 * 60 * 24 * 30); // 设置一个月的有效期
                ((HttpServletResponse) response).addCookie(cookie);
                uvFirstFlag.set(true);
                stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, uv); // set去重
            };
            // 判断用户是否已存在 Cookie 是否首次访问
            if(ArrayUtil.isNotEmpty(cookies)) {
                Arrays.stream(cookies)
                        .filter(cookie -> cookie.getName().equals("uv"))
                        .findFirst() // 找到第一个匹配的 Cookie
                        .map(Cookie::getValue)
                        .ifPresentOrElse(each -> {
                            final Long uvAdded = stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, each);
                            uvFirstFlag.set(uvAdded != null && uvAdded > 0L);
                        }, addResponseCookieTask);
            } else {
                addResponseCookieTask.run();
            }
            final String uip = LinkUtil.getActualIp((HttpServletRequest) request);
            final Long uipAdded = stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UIP_KEY + fullShortUrl, uip);
            boolean uipFirstFlag = uipAdded != null && uipAdded > 0L;
            LambdaQueryWrapper<ShortLinkGoToDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                    .eq(ShortLinkGoToDO::getFullShortUrl, fullShortUrl);
            ShortLinkGoToDO shortLinkGotoDO = shortLinkGoToMapper.selectOne(queryWrapper);
            String gid = shortLinkGotoDO.getGid();
            Date currentDate = new Date();
            int hour = DateUtil.hour(currentDate, true);
            Week week = DateUtil.dayOfWeekEnum(currentDate);
            int weekday = week.getIso8601Value();
            // 构造基础访问统计数据
            final LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .hour(hour)
                    .weekday(weekday)
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(currentDate)
                    .pv(1)
                    .uv(uvFirstFlag.get() ? 1:0)
                    .uip(uipFirstFlag ? 1:0)
                    .build();
            linkAccessStatsMapper.shortLinkStats(List.of(linkAccessStatsDO));
            // 构造区域访问统计数据
            Map<String,Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key",statsLocaleAmapKey);
            localeParamMap.put("ip",uip);
            // 远程调用百度接口
            final String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
            final JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            // 根据高德接口返回结果
            final String infocode = localeResultObj.getString("infocode");
            if(StrUtil.isNotBlank(infocode) && infocode.equals("10000")) {
                final String province = localeResultObj.getString("province");
                // 没有值返回的是[]
                boolean unknownFlag = StrUtil.equals(province, "[]");
                final LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .province(unknownFlag ? "未知" : province)
                        .city(unknownFlag ? "未知" : localeResultObj.getString("city"))
                        .adcode(unknownFlag ? "未知" : localeResultObj.getString("adcode"))
                        .cnt(1)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .country("中国")
                        .date(currentDate)
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
                // 构造操作系统访问统计数据
                final LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .date(currentDate)
                        .os(LinkUtil.getOs((HttpServletRequest) request))
                        .cnt(1)
                        .build();
                linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);
                // 构造浏览器访问统计数据
                final LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                        .browser(LinkUtil.getBrowser((HttpServletRequest) request))
                        .date(currentDate)
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .cnt(1)
                        .build();
                linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);
            }
        } catch (Exception e) {
            throw new ClientException("短链接访问统计异常");
        }

    }

    /**
     * 提取 URL 并进行智能续期
     * @return 有效的 URL，如果过期或格式错误返回 null
     */
    private String extractUrlAndRenew(String originalLinkComposite, String key) {
        String[] split = originalLinkComposite.split("\\|");
        if (split.length < 2) {
            return null;
        }

        long validTime = Long.parseLong(split[0]);
        String originalLink = split[1];

        // 1. 校验业务逻辑是否已过期
        // validTime = -1 表示永久有效
        if (validTime != -1 && System.currentTimeMillis() > validTime) {
            return null; // 已过期
        }

        // 2. 智能续期 (Redis TTL 续期)
        // 这里的逻辑是：只要有人访问，且在有效期内，就重置 Redis 的物理过期时间，防止热点数据被 Redis 清除
        long expireTime;
        if (validTime == -1) {
            // 永久有效的链接：重置为 1 天 (或其他默认时长，保持热点在缓存中)
            expireTime = TimeUnit.DAYS.toMillis(1);
        } else {
            // 有有效期的链接：续期 min(1天, 剩余业务有效期)
            long remainingTime = validTime - System.currentTimeMillis();
            expireTime = Math.min(remainingTime, TimeUnit.DAYS.toMillis(1));
        }

        // 只有当计算出的过期时间大于0时才设置，避免设置错误的 TTL
        if(expireTime > 0) {
            stringRedisTemplate.expire(key, expireTime, TimeUnit.MILLISECONDS);
        }

        return originalLink;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkCreateRespDTO createShortlink(ShortLinkCreateReqDTO requestParam) {
        String suffix = generateSuffix(requestParam);
        String fullShortUrl = requestParam.getDomain() + "/" + suffix;
        final ShortLinkDO shortlinkDO = ShortLinkDO.builder()
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreateType())
                .domain(requestParam.getDomain())
                .describe(requestParam.getDescribe())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .fullShortUrl(fullShortUrl)
                .originUrl(requestParam.getOriginUrl())
                .shortUri(suffix)
                .build();
        // 同时加入路由表
        final ShortLinkGoToDO shortLinkGoToDO = ShortLinkGoToDO.builder()
                .gid(shortlinkDO.getGid())
                .fullShortUrl(shortlinkDO.getFullShortUrl())
                .build();
        // 如果发生布隆冲突，就说明短链接重复了，这个时候数据库就会报key冲突 之前设置了 唯一索引 full_short_url
        try {
            baseMapper.insert(shortlinkDO);
            shortLinkGoToMapper.insert(shortLinkGoToDO);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("短链接：" + fullShortUrl + " 已存在");
        }
        // 缓存预热 创建短链接的时间大于一天存1天，永久短链接也存1天的过期时间
        stringRedisTemplate.opsForValue().set(String.format(GOTO_SHORT_LINK_KEY,
                shortlinkDO.getFullShortUrl()), shortlinkDO.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(shortlinkDO.getValidDate()), TimeUnit.MILLISECONDS);
        // 创建成功后，一定要把“空值缓存”删掉
        // 场景 用户输入了一个不存在的短链接，系统缓存空值，但是用户立刻去创建这个短链接，
        // 创建成功后访问 还没有过缓存时间，系统就会返回404页面，用户就会觉得是bug
        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        // 短链接没有问题就将这个短链接加入布隆过滤器
        shortlinkCachePenetrationBloomFilter.add(shortlinkDO.getFullShortUrl());

        // 【修改点 2】：所有核心业务完成后，发布异步事件去抓取图标
        // 这里可以直接复用更新时的那个 Event 类
        eventPublisher.publishEvent(new UpdateFaviconEvent(
                shortlinkDO.getFullShortUrl(),
                shortlinkDO.getGid(),
                requestParam.getOriginUrl()
        ));
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + shortlinkDO.getFullShortUrl())
                .gid(shortlinkDO.getGid())
                .originUrl(requestParam.getOriginUrl())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortlink(ShortLinkUpdateReqDTO requestParam) {
        // 1. 查出旧数据
        ShortLinkDO shortLinkDO = baseMapper.selectOne(Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 0));

        if (shortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }

        // 2. 记录一下原始链接是否发生了变化 (用于后续判断是否需要更新图标)
        boolean isOriginUrlChanged = !Objects.equals(shortLinkDO.getOriginUrl(), requestParam.getOriginUrl());

        // 3. 判断分组是否改变
        if (Objects.equals(shortLinkDO.getGid(), requestParam.getGid())) {
            // === 情况 A：分组没变，原地更新 ===
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .set(ShortLinkDO::getOriginUrl, requestParam.getOriginUrl())
                    .set(ShortLinkDO::getDescribe, requestParam.getDescribe())
                    .set(ShortLinkDO::getValidDateType, requestParam.getValidDateType())
                    .set(ShortLinkDO::getValidDate,
                            Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
            baseMapper.update(null, updateWrapper);

        } else {
            // === 情况 B：分组改变，先删后插 ===

            // 3.1 更新路由表 (ShortLinkGoTo)
            // 先删旧路由
            shortLinkGoToMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                    .eq(ShortLinkGoToDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkGoToDO::getGid, shortLinkDO.getGid())); //旧GID

            // 插新路由
            ShortLinkGoToDO shortLinkGoToDO = ShortLinkGoToDO.builder()
                    .gid(requestParam.getGid())
                    .fullShortUrl(requestParam.getFullShortUrl())
                    .build();
            shortLinkGoToMapper.insert(shortLinkGoToDO);

            // 3.2 处理主表 (ShortLink)
            // 物理删除旧数据
            baseMapper.deletePhysical(requestParam.getOriginGid(), requestParam.getFullShortUrl());

            // 准备新数据 (复用查出来的对象，修改属性)
            shortLinkDO.setGid(requestParam.getGid());
            shortLinkDO.setOriginUrl(requestParam.getOriginUrl());
            shortLinkDO.setDescribe(requestParam.getDescribe());
            shortLinkDO.setValidDateType(requestParam.getValidDateType());
            shortLinkDO.setValidDate(Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
            shortLinkDO.setId(null); // ID置空，重新生成
            // 注意：这里插入的 shortLinkDO 里 favicon 还是旧的，没关系，异步线程一会儿会改它
            baseMapper.insert(shortLinkDO);
        }

        // 4. 【关键步骤】如果在事务提交前发布事件，需要确保监听器逻辑正确
        // 如果原始链接变了，发布事件通知异步线程去下载图标
        if (isOriginUrlChanged) {
            eventPublisher.publishEvent(new UpdateFaviconEvent(
                    requestParam.getFullShortUrl(),
                    requestParam.getGid(), // 传入最新的 GID
                    requestParam.getOriginUrl()
            ));
        }
        // 删除之前的缓存 TODO 这里的删除逻辑还需要优化
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkPageReqDTO requestParam) {
        final LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus,0)
                .orderByDesc(ShortLinkDO::getCreateTime);
        final IPage<ShortLinkDO>  resultPage = baseMapper.selectPage(requestParam, queryWrapper);
        return resultPage.convert(each -> {
            final ShortLinkPageRespDTO respDTO = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            respDTO.setFullShortUrl("http://" + each.getFullShortUrl());
            return respDTO;
        });
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortlinkCount(List<String> requestParam) {
        // 构造条件 (只写 Where 和 GroupBy 部分)
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getEnableStatus, 0)
                .in(ShortLinkDO::getGid, requestParam)
                .groupBy(ShortLinkDO::getGid);
        return shortLinkMapper.selectGroupCount(queryWrapper);
    }

    /*
    * 生成短链接后缀
    * */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        int customGenerateCount = 0;
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接生成频繁，请稍后再试");
            }
            String originUrl = requestParam.getOriginUrl();
            // 使用UUID避免返回的短链接重复，降低这个重复的概率
            originUrl += UUID.randomUUID().toString();
            shortUri = HashUtil.hashToBase62(originUrl);
            if(!shortlinkCachePenetrationBloomFilter.contains(requestParam.getDomain() + "/" + shortUri)){
                break;
            }
                customGenerateCount++;
        }
        return shortUri;
    }
}
