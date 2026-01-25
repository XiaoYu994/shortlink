package com.xhy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.xhy.shortlink.project.common.biz.user.UserContext;
import com.xhy.shortlink.project.common.convention.exception.ClientException;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.project.common.enums.OrderTagEnum;
import com.xhy.shortlink.project.common.enums.ValidDateTypeEnum;
import com.xhy.shortlink.project.config.GotoDomainWhiteListConfiguration;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.project.dao.event.UpdateFaviconEvent;
import com.xhy.shortlink.project.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.project.dto.resp.*;
import com.xhy.shortlink.project.mq.event.ShortLinkRiskEvent;
import com.xhy.shortlink.project.mq.event.ShortLinkStatsRecordEvent;
import com.xhy.shortlink.project.mq.producer.ShortLinkMessageProducer;
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
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.*;
import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.DEFAULT_COOKIE_VALID_TIME;
import static com.xhy.shortlink.project.common.constant.ShortLinkConstant.TODAY_EXPIRETIME;

/*
* 短链接接口实现层
* */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {
    private final   RBloomFilter<String> shortlinkUriCreateCachePenetrationBloomFilter;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    // 1. 注入事件发布器
    private final ApplicationEventPublisher eventPublisher;
    // mq
    // 1. 专门发监控统计的
    private final ShortLinkMessageProducer<ShortLinkStatsRecordEvent> statsProducer;
    // 2. 专门发缓存清除的
    private final ShortLinkMessageProducer<String> cacheProducer;
    // 3. 专门发 AI 风控的
    private final ShortLinkMessageProducer<ShortLinkRiskEvent> riskProducer;
    // 验证白名单
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;

    // 注入 Caffeine 缓存
    private final Cache<String, String> shortLinkCache;

    private final DefaultRedisScript<Long> statsRankMigrateScript;

    @Value("${short-link.domain.default}")
    private String defaultDomain;

    @SneakyThrows
    @Override
    public void redirect(String shortUri, ServletRequest request, ServletResponse response) {
        // 不带http
        String fullShortUrl = defaultDomain + "/" + shortUri;
        // 缓存 key
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        // 缓存空值key
        String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);

        // 先判断本地缓存中有没有
        String originalLinkComposite = shortLinkCache.getIfPresent(key);
        if (StrUtil.isNotBlank(originalLinkComposite)) {
            String redirectUrl = extractUrlAndRenew(originalLinkComposite, key);
            if (redirectUrl != null) {
                // 统计监控并跳转
                statsProducer.send(buildLinkStatsRecordDTO(fullShortUrl, request, response));
                ((HttpServletResponse) response).sendRedirect(redirectUrl);
                return;
            }
            shortLinkCache.invalidate(key);
        }

        // 1. Redis Cache (L2)
        originalLinkComposite = stringRedisTemplate.opsForValue().get(key);

        // 如果缓存中有值，必须先解析，再判断是否过期/续期，最后跳转
        if (StrUtil.isNotBlank(originalLinkComposite)) {
            // 将 Redis 查到的热点数据写入 Caffeine
            shortLinkCache.put(key, originalLinkComposite);
            String redirectUrl = extractUrlAndRenew(originalLinkComposite, key);
            if (redirectUrl != null) {
                statsProducer.send(buildLinkStatsRecordDTO(fullShortUrl, request, response));
                ((HttpServletResponse) response).sendRedirect(redirectUrl);
                return;
            }
            // 如果返回 null，说明逻辑已过期或数据异常，视为缓存未命中，继续往下走（或者直接走回源逻辑）
            // 这里为了稳健，如果数据异常通常应该删缓存并重新查库，继续往下执行
            stringRedisTemplate.delete(key);
        }

        // 2. 布隆过滤器拦截 (解决缓存穿透)
        if (!shortlinkUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
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
        final RLock lock = redissonClient.getLock(String.format(LOOK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            // 5. 双重检查 (Double Check)
            originalLinkComposite = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(originalLinkComposite)) {
                // 加入本地缓存
                shortLinkCache.put(key, originalLinkComposite);
                // 锁内的解析和续期（防止在等待锁的过程中别人已经查好放入缓存了）
                String redirectUrl = extractUrlAndRenew(originalLinkComposite, key);
                if (redirectUrl != null) {
                    statsProducer.send(buildLinkStatsRecordDTO(fullShortUrl, request, response));
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
                stringRedisTemplate.opsForValue().set(keyIsNull, "-", DEFAULT_CACHE_VALID_TIME_FOR_GOTO, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }

            // 6.2 查详情表
            LambdaQueryWrapper<ShortLinkDO> linkWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getGid, shortLinkGoToDO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus());
            ShortLinkDO shortLinkDO = baseMapper.selectOne(linkWrapper);

            if (shortLinkDO == null || (shortLinkDO.getValidDate() != null && shortLinkDO.getValidDate().before(new Date()))) {
                stringRedisTemplate.opsForValue().set(keyIsNull, "-", DEFAULT_CACHE_VALID_TIME_FOR_GOTO, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }

            // 7. 重建缓存
            long validTimeStamp = (shortLinkDO.getValidDate() != null) ? shortLinkDO.getValidDate().getTime() : -1;
            String cacheValue = validTimeStamp + "|" + shortLinkDO.getOriginUrl();
            // 计算初始 TTL (例如：过期时间 - 当前时间，或者默认 1 天)
            long initialTTL = LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate());
            stringRedisTemplate.opsForValue().set(key, cacheValue, initialTTL, TimeUnit.MILLISECONDS);
            // 加入本地缓存
            shortLinkCache.put(key, cacheValue);
            statsProducer.send(buildLinkStatsRecordDTO(fullShortUrl, request, response));
            ((HttpServletResponse) response).sendRedirect(shortLinkDO.getOriginUrl());
        } finally {
            lock.unlock();
        }
    }

    // 构造短链接访问统计参数
    private ShortLinkStatsRecordEvent buildLinkStatsRecordDTO(String fullShortUrl, ServletRequest request, ServletResponse response) {
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
            AtomicReference<String> uv = new AtomicReference<>();
            Runnable addResponseCookieTask = () -> {
                uv.set(UUID.fastUUID().toString());
                final Cookie cookie = new Cookie("uv", uv.get());
                cookie.setMaxAge(DEFAULT_COOKIE_VALID_TIME); // 设置一个月的有效期
                ((HttpServletResponse) response).addCookie(cookie);
                uvFirstFlag.set(true);
                stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, uv.get()); // set去重
            };
            // 判断用户是否已存在 Cookie 是否首次访问
            if(ArrayUtil.isNotEmpty(cookies)) {
                Arrays.stream(cookies)
                        .filter(cookie -> cookie.getName().equals("uv"))
                        .findFirst() // 找到第一个匹配的 Cookie
                        .map(Cookie::getValue)
                        .ifPresentOrElse(each -> {
                            uv.set(each);
                            // 添加到 Set，如果集合中不存在则返回 1（新用户），存在则返回 0（老用户）
                            final Long uvAdded = stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, each);
                            uvFirstFlag.set(uvAdded != null && uvAdded > 0L);
                        }, addResponseCookieTask);
            } else {
                addResponseCookieTask.run();
            }
            final String remoteAddr = LinkUtil.getActualIp((HttpServletRequest) request);
            final Long uipAdded = stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UIP_KEY + fullShortUrl, remoteAddr);
            boolean uipFirstFlag = uipAdded != null && uipAdded > 0L;
            String os = LinkUtil.getOs(((HttpServletRequest) request));
            String browser = LinkUtil.getBrowser(((HttpServletRequest) request));
            String device = LinkUtil.getDevice(((HttpServletRequest) request));
            String network = LinkUtil.getNetwork(((HttpServletRequest) request));
            return ShortLinkStatsRecordEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .fullShortUrl(fullShortUrl)
                    .device(device)
                    .os(os)
                    .network(network)
                    .browser(browser)
                    .currentDate(new Date())
                    .remoteAddr(remoteAddr)
                    .uipFirstFlag(uipFirstFlag)
                    .uvFirstFlag(uvFirstFlag.get())
                    .uv(uv.get())
                    .build();
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
        // 验证短链接是否是白名单中的链接
        verificationWhitelist(requestParam.getOriginUrl());
        String suffix = generateSuffix(requestParam);
        String fullShortUrl = StrBuilder.create(defaultDomain)
                .append("/")
                .append(suffix).toString();
        final ShortLinkDO shortlinkDO = ShortLinkDO.builder()
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreatedType())
                .domain(defaultDomain)
                .description(requestParam.getDescription())
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
        // 如果发生布隆误判，就说明短链接重复了，这个时候数据库就会报key冲突 之前设置了 唯一索引 full_short_url
        try {
            baseMapper.insert(shortlinkDO);
            shortLinkGoToMapper.insert(shortLinkGoToDO);
        } catch (DuplicateKeyException e) {
            // 说明布隆过滤器误判被数据库唯一索引捕获，需要重新加入到布隆过滤器中(也有可能是Redis宕机，布隆过滤器数据丢失)
            if(!shortlinkUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
                shortlinkUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
            }
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
        shortlinkUriCreateCachePenetrationBloomFilter.add(shortlinkDO.getFullShortUrl());

        // 【修改点 2】：所有核心业务完成后，发布异步事件去抓取图标
        // 这里可以直接复用更新时的那个 Event 类
        eventPublisher.publishEvent(new UpdateFaviconEvent(
                shortlinkDO.getFullShortUrl(),
                shortlinkDO.getGid(),
                requestParam.getOriginUrl()
        ));

        // 发送 AI 风控审核消息 (异步)
        riskProducer.send(ShortLinkRiskEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .fullShortUrl(shortlinkDO.getFullShortUrl())
                .originUrl(shortlinkDO.getOriginUrl())
                .gid(shortlinkDO.getGid())
                 .userId(Long.parseLong(UserContext.getUserId()))
                .build());
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + shortlinkDO.getFullShortUrl())
                .gid(shortlinkDO.getGid())
                .originUrl(requestParam.getOriginUrl())
                .build();
    }

    @Override
    public ShortLinkCreateRespDTO createShortLinkByLock(ShortLinkCreateReqDTO requestParam) {
        verificationWhitelist(requestParam.getOriginUrl());
        String fullShortUrl ;
        ShortLinkDO shortlinkDO;
        final RLock lock = redissonClient.getLock(SHORT_LINK_CREATE_LOCK_KEY);
        lock.lock();
        try {
            String suffix = generateSuffixByLock(requestParam);
            fullShortUrl = StrBuilder.create(defaultDomain)
                    .append("/")
                    .append(suffix).toString();
             shortlinkDO = ShortLinkDO.builder()
                    .gid(requestParam.getGid())
                    .createdType(requestParam.getCreatedType())
                    .domain(defaultDomain)
                    .description(requestParam.getDescription())
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
        } finally {
            lock.unlock();
        }
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
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        final List<String> originUrlList = requestParam.getOriginUrls();
        final List<String> describeList = requestParam.getDescription();
        List<ShortLinkBaseInfoRespDTO> resultList = new ArrayList<>();
        for(int i = 0; i < originUrlList.size(); i++) {
            ShortLinkCreateReqDTO shortLinkCreateReqDTO = BeanUtil.toBean(requestParam, ShortLinkCreateReqDTO.class);
            shortLinkCreateReqDTO.setOriginUrl(originUrlList.get(i));
            shortLinkCreateReqDTO.setDescription(describeList.get(i));
            try {
                ShortLinkCreateRespDTO shortlink = createShortlink(shortLinkCreateReqDTO);
                resultList.add(ShortLinkBaseInfoRespDTO.builder()
                        .fullShortUrl(shortlink.getFullShortUrl())
                        .originUrl(shortlink.getOriginUrl())
                        .description(describeList.get(i))
                        .build());
            } catch (Throwable e) {
                log.error("批量创建短链接失败，原始参数：{}", originUrlList.get(i), e);
            }
        }
        return ShortLinkBatchCreateRespDTO.builder()
                .total(resultList.size())
                .baseLinkInfos(resultList)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortlink(ShortLinkUpdateReqDTO requestParam) {
        // 验证短链接是否是白名单中的链接
        verificationWhitelist(requestParam.getOriginUrl());
        // 1. 查出旧数据
        ShortLinkDO shortLinkDO = baseMapper.selectOne(Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus()));

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
                    .set(ShortLinkDO::getDescription, requestParam.getDescription())
                    .set(ShortLinkDO::getValidDateType, requestParam.getValidDateType())
                    .set(ShortLinkDO::getValidDate,
                            Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
            baseMapper.update(null, updateWrapper);

        } else {
            // === 情况 B：分组改变，先删后插 ===
            //引入读写锁
            RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, requestParam.getFullShortUrl()));
            RLock rLock = readWriteLock.writeLock();
            rLock.lock();
            try {
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
                shortLinkDO.setDescription(requestParam.getDescription());
                shortLinkDO.setValidDateType(requestParam.getValidDateType());
                shortLinkDO.setValidDate(Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
                shortLinkDO.setId(null); // ID置空，重新生成
                // 注意：这里插入的 shortLinkDO 里 favicon 还是旧的，没关系，异步线程一会儿会改它
                baseMapper.insert(shortLinkDO);
                // 更新 Redis 中的统计的今日数据
                // 逻辑：从旧分组的 ZSet 取出分数，添加到新分组 ZSet，然后从旧分组删除
                String todayStr = DateUtil.today();
                String fullShortUrl = requestParam.getFullShortUrl();
                String oldGid = requestParam.getOriginGid();
                String newGid = requestParam.getGid();
                // 过期时间

                // 需要迁移的三种统计类型
                List<String> statsTypes = Arrays.asList(
                        OrderTagEnum.TODAY_PV.getValue(),
                        OrderTagEnum.TODAY_UV.getValue(),
                        OrderTagEnum.TODAY_UIP.getValue()
                );
                for (String statsType : statsTypes) {
                    String oldKey = String.format(RANK_KEY, statsType, oldGid, todayStr);
                    String newKey = String.format(RANK_KEY, statsType, newGid, todayStr);

                    // 执行 Lua 脚本：原子性完成 查旧 -> 删旧 -> 插新 -> 设TTL
                    stringRedisTemplate.execute(
                            statsRankMigrateScript, // 你的脚本对象
                            Arrays.asList(oldKey, newKey), // KEYS
                            fullShortUrl, TODAY_EXPIRETIME // ARGV
                    );
                }
            } finally {
                rLock.unlock();
            }
        }

        // 4. 【关键步骤】如果在事务提交前发布事件，需要确保监听器逻辑正确
        // 如果原始链接变了，发布事件通知异步线程去下载图标
        if (isOriginUrlChanged) {
            eventPublisher.publishEvent(new UpdateFaviconEvent(
                    requestParam.getFullShortUrl(),
                    requestParam.getGid(), // 传入最新的 GID
                    requestParam.getOriginUrl()
            ));
            // 发送 AI 风控审核消息 (异步)
            riskProducer.send(ShortLinkRiskEvent.builder()
                     .eventId(UUID.randomUUID().toString())
                    .fullShortUrl(requestParam.getFullShortUrl())
                    .originUrl(requestParam.getOriginUrl())
                    .gid(requestParam.getGid())
                    .userId(Long.parseLong(UserContext.getUserId()))
                    .build());
        }
        //直接删除之前的缓存和过期的空缓存
        stringRedisTemplate.delete(Arrays.asList(
                String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()),
                String.format(GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl())
        ));
        // 发送 MQ 广播消息通知清除本地 Caffeine (L1)
        cacheProducer.send(requestParam.getFullShortUrl());
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkPageReqDTO requestParam) {
        // 场景 A：如果用户点击了“今日访问量/人数/IP数”排序
        if (StrUtil.equalsAny(requestParam.getOrderTag(), OrderTagEnum.TODAY_PV.getValue(), OrderTagEnum.TODAY_UV.getValue(), OrderTagEnum.TODAY_UIP.getValue())) {
            return pageByRedisRank(requestParam);
        }

        // 场景 B：默认排序（按创建时间），走原来的高性能 MySQL 查询
        IPage<ShortLinkDO> resultPage = baseMapper.pageLink(requestParam); // 这里用不带 JOIN 的简单 SQL
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://" + result.getDomain());
            // 从 Redis 填充今日数据用于展示（但不用于排序）
            fillTodayStats(result);
            return result;
        });
    }

    /**
     * 核心：基于 Redis ZSet 的分页查询 ：Redis (今日有数据) -> MySQL (今日无数据)
     */
    private IPage<ShortLinkPageRespDTO> pageByRedisRank(ShortLinkPageReqDTO req) {
        String todayStr = DateUtil.today();
        // 获取今天的起始时间 (例如 2026-01-25 00:00:00)
        Date todayStart = DateUtil.beginOfDay(new Date());
        // 1. 确定要查哪个榜单
        String rankKey = String.format(RANK_KEY, req.getOrderTag(), req.getGid(), todayStr);

        // 2. 计算分页范围
        long current = req.getCurrent();
        long size = req.getSize();
        long start = (current - 1) * size;
        long end = start + size - 1;

        try {
            // 3. 查询 Redis 中“有流量”的总数
            Long redisTotal = stringRedisTemplate.opsForZSet().zCard(rankKey);
            redisTotal = redisTotal == null ? 0 : redisTotal;

            // 4. 查询 MySQL 中“无流量”的总数 (last_access_time < 今天)
            // 也可以直接用 selectCount 查总数减去 redisTotal，但这样查更精准
            long dbFallbackTotal = baseMapper.countLinkFallback(req.getGid(), todayStart);

            // 总条数 = Redis热数据 + DB冷数据
            long total = redisTotal + dbFallbackTotal;

            List<ShortLinkPageRespDTO> resultList = new ArrayList<>();
            // 场景 A: 请求范围完全在 Redis 内
            if (start < redisTotal && end < redisTotal) {
                Set<String> urls = stringRedisTemplate.opsForZSet().reverseRange(rankKey, start, end);
                resultList.addAll(buildResultByUrls(urls, req.getGid()));
            }
            // 场景 B: 请求范围跨越 Redis 和 MySQL (接壤处)
            else if (start < redisTotal) {
                // 1. 先取 Redis 剩下的部分
                Set<String> urls = stringRedisTemplate.opsForZSet().reverseRange(rankKey, start, redisTotal - 1);
                resultList.addAll(buildResultByUrls(urls, req.getGid()));

                // 2. 计算需要从 DB 补多少条
                long needMore = size - resultList.size();

                // 3. 从 DB 查补位数据 (偏移量 offset = 0，因为是接在 Redis 屁股后面)
                List<ShortLinkDO> dbList = baseMapper.pageLinkFallback(
                        req.getGid(),
                        todayStart,
                        0,
                        needMore
                );
                resultList.addAll(beanToDtoList(dbList));
            }
            // 场景 C: 请求范围完全在 MySQL 内 (纯冷数据)
            else {
                // 计算 DB 的偏移量
                // 公式：当前请求的全局 offset - Redis 的总数
                long dbOffset = start - redisTotal;

                List<ShortLinkDO> dbList = baseMapper.pageLinkFallback(
                        req.getGid(),
                        todayStart,
                        dbOffset,
                        size
                );
                resultList.addAll(beanToDtoList(dbList));
            }

            // ==================== 返回分页对象 ====================
            IPage<ShortLinkPageRespDTO> page = new Page<>();
            page.setRecords(resultList);
            page.setTotal(total);
            page.setCurrent(current);
            page.setSize(size);
            return page;
        } catch (Exception e) {
            // ================= 触发降级 =================
            log.error("Redis排行榜查询失败，触发降级策略，转为纯数据库查询。Gid: {}", req.getGid(), e);
            // 降级处理：直接查数据库全量列表（忽略 last_access_time 过滤）
            // 注意：此时无法按“今日PV”排序，只能按默认排序（如创建时间或总PV）
            return fallbackToBaseQuery(req);
        }
    }

    /**
     * 降级查询方法：完全不依赖 Redis，直接查库
     */
    private IPage<ShortLinkPageRespDTO> fallbackToBaseQuery(ShortLinkPageReqDTO req) {
        IPage<ShortLinkDO> resultPage = baseMapper.pageLink(req);
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://" + result.getDomain());
            // 降级模式下，今日数据无法获取，置为 0
            result.setTodayPv(0);
            result.setTodayUv(0);
            result.setTodayUip(0);
            return result;
        });
    }

    // 辅助方法：Redis 结果组装
    private List<ShortLinkPageRespDTO> buildResultByUrls(Set<String> urls, String gid) {
        if (urls == null || urls.isEmpty()) return new ArrayList<>();

        // 1. 批量查 DB 基础信息
        List<ShortLinkDO> linkDOList = baseMapper.selectList(Wrappers.<ShortLinkDO>lambdaQuery()
                .in(ShortLinkDO::getFullShortUrl, urls)
                .eq(ShortLinkDO::getGid, gid)
                .eq(ShortLinkDO::getDelFlag, 0));

        // 2. 内存重排序
        Map<String, ShortLinkDO> linkMap = linkDOList.stream()
                .collect(Collectors.toMap(ShortLinkDO::getFullShortUrl, Function.identity()));

        List<ShortLinkPageRespDTO> list = new ArrayList<>();
        String todayStr = DateUtil.today();
        String pvKey = String.format(RANK_KEY, OrderTagEnum.TODAY_PV.getValue(), gid, todayStr);
        String uvKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UV.getValue(), gid, todayStr);
        String uipKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UIP.getValue(), gid, todayStr);
        for (String url : urls) {
            ShortLinkDO dbLink = linkMap.get(url);
            if (dbLink != null) {
                ShortLinkPageRespDTO dto = BeanUtil.toBean(dbLink, ShortLinkPageRespDTO.class);
                dto.setDomain("http://" + dto.getDomain());
                // 1. 查 PV
                Double pv = stringRedisTemplate.opsForZSet().score(pvKey, url);
                dto.setTodayPv(pv == null ? 0 : pv.intValue());

                // 2. 查 UV
                Double uv = stringRedisTemplate.opsForZSet().score(uvKey, url);
                dto.setTodayUv(uv == null ? 0 : uv.intValue());

                // 3. 查 UIP
                Double uip = stringRedisTemplate.opsForZSet().score(uipKey, url);
                dto.setTodayUip(uip == null ? 0 : uip.intValue());
                list.add(dto);
            }
        }
        return list;
    }
    // 辅助方法：DB 冷数据转换 (默认今日数据为0)
    private List<ShortLinkPageRespDTO> beanToDtoList(List<ShortLinkDO> dbList) {
        return dbList.stream().map(each -> {
            ShortLinkPageRespDTO dto = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            dto.setDomain("http://" + dto.getDomain());
            dto.setTodayPv(0);
            dto.setTodayUv(0);
            dto.setTodayUip(0);
            return dto;
        }).collect(Collectors.toList());
    }
    /**
     * 从 Redis ZSet 中获取今日实时统计数据
     */
    public void fillTodayStats(ShortLinkPageRespDTO result) {

        // 1. 获取当前日期，格式必须与写入时保持一致 (yyyy-MM-dd)
        String todayDate = DateUtil.today();

        // 2. 构造 Redis Key (注意使用 {gid} hash tag)
        String gid = result.getGid();
        String fullShortUrl = result.getFullShortUrl();

        String pvKey = String.format(RANK_KEY,OrderTagEnum.TODAY_PV.getValue(), gid, todayDate);
        String uvKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UV.getValue(),gid, todayDate);
        String uipKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UIP.getValue(),gid, todayDate);

        // 3. 批量查询 Redis (Pipeline 优化可选，但这里单次查询也可以)
        // score 返回 Double，如果 member 不存在则返回 null
        Double pvScore = stringRedisTemplate.opsForZSet().score(pvKey, fullShortUrl);
        Double uvScore = stringRedisTemplate.opsForZSet().score(uvKey, fullShortUrl);
        Double uipScore = stringRedisTemplate.opsForZSet().score(uipKey, fullShortUrl);

        // 4. 填充数据 (Null Safe 处理)
        result.setTodayPv(pvScore == null ? 0 : pvScore.intValue());
        result.setTodayUv(uvScore == null ? 0 : uvScore.intValue());
        result.setTodayUip(uipScore == null ? 0 : uipScore.intValue());
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortlinkCount(List<String> requestParam) {
        // 构造条件 (只写 Where 和 GroupBy 部分)
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus())
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
        String originUrl = requestParam.getOriginUrl();
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接生成频繁，请稍后再试");
            }
            shortUri = HashUtil.hashToBase62(originUrl);
            if(!shortlinkUriCreateCachePenetrationBloomFilter.contains(defaultDomain + "/" + shortUri)){
                break;
            }
            originUrl += UUID.randomUUID().toString();
            customGenerateCount++;
        }
        return shortUri;
    }

    /*
    * 通过分布式锁判断
    * */
    private String generateSuffixByLock(ShortLinkCreateReqDTO requestParam) {
        int customGenerateCount = 0;
        String shorUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后再试");
            }
            String originUrl = requestParam.getOriginUrl();
            originUrl += UUID.randomUUID().toString();
            shorUri = HashUtil.hashToBase62(originUrl);
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, defaultDomain + "/" + shorUri)
                    .eq(ShortLinkDO::getDelFlag, 0);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);
            if (shortLinkDO == null) {
                break;
            }
            customGenerateCount++;
        }
        return shorUri;
    }

    private void verificationWhitelist(String originUrl) {
        final Boolean enable = gotoDomainWhiteListConfiguration.getEnable();
        if(enable == null || !enable)  {
            return;
        }
        final String domain = LinkUtil.extractDomain(originUrl);
        if(StrUtil.isBlank(domain)) {
            throw new ClientException("跳转链接填写错误");
        }
        final List<String> details = gotoDomainWhiteListConfiguration.getDetails();
        if(!details.contains(domain)) {
            throw new ClientException("请生成以下网站跳转链接：" + gotoDomainWhiteListConfiguration.getNames());
        }
    }
}
