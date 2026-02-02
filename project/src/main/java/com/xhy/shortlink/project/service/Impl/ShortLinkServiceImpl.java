package com.xhy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ArrayUtil;
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
import com.xhy.shortlink.project.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToColdDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.project.dao.event.UpdateFaviconEvent;
import com.xhy.shortlink.project.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.project.dto.resp.*;
import com.xhy.shortlink.project.mq.event.ShortLinkExpireArchiveEvent;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
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
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ShortLinkMessageProducer<ShortLinkStatsRecordEvent> statsProducer;
    private final ShortLinkMessageProducer<String> cacheProducer;
    private final ShortLinkMessageProducer<ShortLinkRiskEvent> riskProducer;
    private final ShortLinkMessageProducer<ShortLinkExpireArchiveEvent> expireArchiveProducer;
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;
    private final Cache<String, String> shortLinkCache;
    private final DefaultRedisScript<Long> statsRankMigrateScript;
    private final PlatformTransactionManager transactionManager;

    @Value("${short-link.domain.default}")
    private String defaultDomain;

    private static final String HTTP_PROTOCOL  = "http://";
    public static final String PAGE_NOT_FOUND = "/page/notfound";


    @SneakyThrows
    @Override
    public void redirect(String shortUri, ServletRequest request, ServletResponse response) {
        String fullShortUrl = defaultDomain + "/" + shortUri;

        // 1. 尝试从多级缓存获取 (L1 Caffeine -> L2 Redis)
        ShortLinkCacheObj cacheObj = getFromCache(fullShortUrl);
        if (cacheObj != null) {
            executeRedirect(fullShortUrl, cacheObj, request, response);
            return;
        }

        // 2. 穿透拦截 (布隆过滤器判断是否存在 + 空值缓存判断)
        if (isPossiblePenetration(fullShortUrl)) {
            ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
            return;
        }

        // 3. 回源查询 (加锁解决击穿)
        processWithLock(fullShortUrl, request, response);
    }

    /**
     * 获取缓存（封装 L1 和 L2）
     */
    private ShortLinkCacheObj getFromCache(String fullShortUrl) {
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        // 先查本地缓存
        String composite = shortLinkCache.getIfPresent(key);
        if (CharSequenceUtil.isBlank(composite)) {
            // 本地没有，查 Redis
            composite = stringRedisTemplate.opsForValue().get(key);
            if (CharSequenceUtil.isBlank(composite)) {
                return null;
            }
            // 查到 Redis 顺手回填给本地缓存
            shortLinkCache.put(key, composite);
        }

        // 解析并校验业务有效期与续期
        ShortLinkCacheObj cacheObj = parseCache(composite, key);
        if (cacheObj == null) {
            // 如果解析失败或已过期，清理 L1 和 L2
            shortLinkCache.invalidate(key);
            stringRedisTemplate.delete(key);
        }
        return cacheObj;
    }

    /**
     * 判断是否是无效请求（缓存穿透场景）
     */
    private boolean isPossiblePenetration(String fullShortUrl) {
        // 布隆过滤器不包含，直接判定不存在
        if (!shortlinkUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
            return true;
        }
        // 存在空值缓存，说明之前查库确认过没这条记录
        String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
        return CharSequenceUtil.isNotBlank(stringRedisTemplate.opsForValue().get(keyIsNull));
    }

    /**
     * 加锁回源查询逻辑
     */
    private void processWithLock(String fullShortUrl, ServletRequest request, ServletResponse response) throws IOException {
        RLock lock = redissonClient.getLock(String.format(LOOK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            // 双重检查：拿锁期间别人可能已经把缓存建好了
            ShortLinkCacheObj cacheObj = getFromCache(fullShortUrl);
            if (cacheObj != null) {
                executeRedirect(fullShortUrl, cacheObj, request, response);
                return;
            }

            // 严谨的双重检查：再次判定空值缓存
            if (isPossiblePenetration(fullShortUrl)) {
                ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
                return;
            }

            // 查询数据库（冷热分离逻辑）
            ShortLinkCacheObj dbObj = loadFromDb(fullShortUrl);
            if (dbObj != null) {
                // 回填多级缓存
                rebuildCache(fullShortUrl, dbObj);
                executeRedirect(fullShortUrl, dbObj, request, response);
            } else {
                // 库里也没有，写空值缓存
                String keyIsNull = String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
                stringRedisTemplate.opsForValue().set(keyIsNull, "-", DEFAULT_CACHE_VALID_TIME_FOR_GOTO, TimeUnit.SECONDS);
                ((HttpServletResponse) response).sendRedirect(PAGE_NOT_FOUND);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 数据库冷热查询策略
     */
    private ShortLinkCacheObj loadFromDb(String fullShortUrl) {
        // 1. 查热表路由
        ShortLinkGoToDO goToDO = shortLinkGoToMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                .eq(ShortLinkGoToDO::getFullShortUrl, fullShortUrl));

        if (goToDO != null) {
            ShortLinkDO linkDO = baseMapper.selectOne(Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getGid, goToDO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus()));
            if (linkDO != null && isNotExpired(linkDO.getValidDate())) {
                return new ShortLinkCacheObj(linkDO.getOriginUrl(), linkDO.getGid(), linkDO.getValidDate());
            }
            return null;
        }

        // 2. 热表没找到，尝试查冷库（兜底）
        ShortLinkGoToColdDO coldGoToDO = shortLinkGoToColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));

        if (coldGoToDO != null) {
            ShortLinkColdDO coldDO = shortLinkColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkColdDO::getGid, coldGoToDO.getGid())
                    .eq(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus()));
            if (coldDO != null && isNotExpired(coldDO.getValidDate())) {
                return new ShortLinkCacheObj(coldDO.getOriginUrl(), coldDO.getGid(), coldDO.getValidDate());
            }
        }
        return null;
    }


    /**
     * 冷库回热入口：由统计消费触发，达到阈值后迁回热库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rehotColdLink(String fullShortUrl, String gid) {
        try {
            ShortLinkColdDO coldDO = shortLinkColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getGid, gid)
                    .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl));
            if (coldDO == null) {
                return;
            }
            ShortLinkGoToColdDO coldGoToDO = shortLinkGoToColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                    .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
            if (coldGoToDO == null) {
                return;
            }
            ShortLinkDO existsHot = baseMapper.selectOne(Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getGid, gid));
            if (existsHot == null) {
                ShortLinkDO hotDO = BeanUtil.toBean(coldDO, ShortLinkDO.class);
                baseMapper.insert(hotDO);
                ShortLinkGoToDO hotGoTo = BeanUtil.toBean(coldGoToDO, ShortLinkGoToDO.class);
                shortLinkGoToMapper.insert(hotGoTo);
            }
            shortLinkColdMapper.delete(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getGid, gid)
                    .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl));
            shortLinkGoToColdMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                    .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
            stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
            stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
            cacheProducer.send(fullShortUrl);
        } catch (DuplicateKeyException e) {
            shortLinkColdMapper.delete(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getGid, gid)
                    .eq(ShortLinkColdDO::getFullShortUrl, fullShortUrl));
            shortLinkGoToColdMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                    .eq(ShortLinkGoToColdDO::getFullShortUrl, fullShortUrl));
        } catch (Exception e) {
            log.error("冷库回热失败，fullShortUrl={}", fullShortUrl, e);
        }
    }

    /**
     * 重定向执行与统计埋点
     */
    @SneakyThrows
    private void executeRedirect(String fullShortUrl, ShortLinkCacheObj cacheObj, ServletRequest request, ServletResponse response) {
        statsProducer.send(buildLinkStatsRecordDTO(fullShortUrl, cacheObj.getGid(), request, response));
        ((HttpServletResponse) response).sendRedirect(cacheObj.getOriginUrl());
    }

    /**
     * 重建多级缓存
     */
    private void rebuildCache(String fullShortUrl, ShortLinkCacheObj dbObj) {
        String key = String.format(GOTO_SHORT_LINK_KEY, fullShortUrl);
        long validTimeStamp = (dbObj.getValidDate() != null) ? dbObj.getValidDate().getTime() : -1;
        String cacheValue = String.format("%d|%s|%s", validTimeStamp, dbObj.getOriginUrl(), dbObj.getGid());
        long initialTTL = LinkUtil.getLinkCacheValidTime(dbObj.getValidDate());

        stringRedisTemplate.opsForValue().set(key, cacheValue, initialTTL, TimeUnit.MILLISECONDS);
        shortLinkCache.put(key, cacheValue);
    }

    /**
     * 判断时间是否未过期
     */
    private boolean isNotExpired(Date validDate) {
        return validDate == null || validDate.after(new Date());
    }

    // 新增一个内部类，方便传输解析结果
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ShortLinkCacheObj {
        private String originUrl;
        private String gid;
        private Date validDate;
    }
    // 构造短链接访问统计参数
    private ShortLinkStatsRecordEvent buildLinkStatsRecordDTO(String fullShortUrl, String gid, ServletRequest request, ServletResponse response) {
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
            AtomicReference<String> uv = new AtomicReference<>();
            // 确保用户有一个 Cookie，并拿到这个 UUID
            Runnable generateNewCookieTask = () -> {
                uv.set(UUID.fastUUID().toString());
                final Cookie cookie = new Cookie("uv", uv.get());
                cookie.setMaxAge(DEFAULT_COOKIE_VALID_TIME); // 设置一个月的有效期
                cookie.setPath("/"); // 设置路径
                ((HttpServletResponse) response).addCookie(cookie);
                uvFirstFlag.set(true);
                stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, uv.get()); // set去重
            };
            // 判断用户是否已存在 Cookie 是否首次访问
            if(ArrayUtil.isNotEmpty(cookies)) {
                Arrays.stream(cookies)
                        .filter(cookie -> "uv".equals(cookie.getName()))
                        .findFirst() // 找到第一个匹配的 Cookie
                        .map(Cookie::getValue)
                        .ifPresentOrElse(uv::set, generateNewCookieTask);
            } else {
                generateNewCookieTask.run();
            }
            final String remoteAddr = LinkUtil.getActualIp((HttpServletRequest) request);
            String os = LinkUtil.getOs(((HttpServletRequest) request));
            String browser = LinkUtil.getBrowser(((HttpServletRequest) request));
            String device = LinkUtil.getDevice(((HttpServletRequest) request));
            String network = LinkUtil.getNetwork(((HttpServletRequest) request));
            return ShortLinkStatsRecordEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .fullShortUrl(fullShortUrl)
                    .device(device)
                    .os(os)
                    .gid(gid)
                    .network(network)
                    .browser(browser)
                    .currentDate(new Date())
                    .remoteAddr(remoteAddr)
                    .uv(uv.get())
                    .build();
    }

    /**
     * 解析缓存字符串并判断有效期
     */
    private ShortLinkCacheObj parseCache(String originalLinkComposite, String key) {
        String[] split = originalLinkComposite.split("\\|");
        if (split.length < 2) {
            return null;
        }

        long validTime = Long.parseLong(split[0]);
        String originalLink = split[1];
        String gid = split[2];
        // 1. 校验业务逻辑是否已过期
        // validTime = -1 表示永久有效
        if (validTime != -1 && System.currentTimeMillis() > validTime) {
            // 已过期，需要让外层知道去删缓存
            stringRedisTemplate.delete(key);
            return null;
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
        Date validDate = validTime == -1 ? null : new Date(validTime);
        return new ShortLinkCacheObj(originalLink, gid, validDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkCreateRespDTO createShortlink(ShortLinkCreateReqDTO requestParam) {
        return doCreateShortlink(requestParam);
    }

    private ShortLinkCreateRespDTO doCreateShortlink(ShortLinkCreateReqDTO requestParam) {
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
        // 存入的缓存结构 有效期 | 原始链接 | gid
        // 缓存预热 创建短链接的时间大于一天存1天，永久短链接也存1天的过期时间
        // 计算有效期时间戳 (用于缓存内部的双重校验)
        long validTimeStamp = (shortlinkDO.getValidDate() != null) ? shortlinkDO.getValidDate().getTime() : -1;
        String cacheValue = String.format("%d|%s|%s",
                validTimeStamp,
                shortlinkDO.getOriginUrl(),
                shortlinkDO.getGid()
        );
        // 计算 Redis Key 的物理过期时间
        // (LinkUtil.getLinkCacheValidTime 逻辑：如果有效期不足1天则按剩余时间存，否则默认存1天)
        long initialTTL = LinkUtil.getLinkCacheValidTime(shortlinkDO.getValidDate());
        stringRedisTemplate.opsForValue().set(String.format(GOTO_SHORT_LINK_KEY, shortlinkDO.getFullShortUrl()),
                cacheValue,
                initialTTL,
                TimeUnit.MILLISECONDS);
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
        // 发送过期归档消息 (延迟)
        if (Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.CUSTOM.getType())
                && requestParam.getValidDate() != null) {
            expireArchiveProducer.send(ShortLinkExpireArchiveEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .gid(shortlinkDO.getGid())
                    .fullShortUrl(shortlinkDO.getFullShortUrl())
                    .expireAt(requestParam.getValidDate())
                    .userId(Long.parseLong(UserContext.getUserId()))
                    .stage(ShortLinkExpireArchiveEvent.Stage.FREEZE)
                    .build());
        }
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl(HTTP_PROTOCOL + shortlinkDO.getFullShortUrl())
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
            long validTimeStamp = (shortlinkDO.getValidDate() != null) ? shortlinkDO.getValidDate().getTime() : -1;
            String cacheValue = String.format("%d|%s|%s",
                    validTimeStamp,
                    shortlinkDO.getOriginUrl(),
                    shortlinkDO.getGid()
            );
            // 计算 Redis Key 的物理过期时间
            // (LinkUtil.getLinkCacheValidTime 逻辑：如果有效期不足1天则按剩余时间存，否则默认存1天)
            long initialTTL = LinkUtil.getLinkCacheValidTime(shortlinkDO.getValidDate());
            stringRedisTemplate.opsForValue().set(String.format(GOTO_SHORT_LINK_KEY, shortlinkDO.getFullShortUrl()),
                    cacheValue,
                    initialTTL,
                    TimeUnit.MILLISECONDS);
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
                    .fullShortUrl(HTTP_PROTOCOL + shortlinkDO.getFullShortUrl())
                    .gid(shortlinkDO.getGid())
                    .originUrl(requestParam.getOriginUrl())
                    .build();

    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        final List<String> originUrlList = requestParam.getOriginUrls();
        final List<String> describeList = requestParam.getDescription();
        List<ShortLinkBaseInfoRespDTO> resultList = new ArrayList<>();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for(int i = 0; i < originUrlList.size(); i++) {
            ShortLinkCreateReqDTO shortLinkCreateReqDTO = BeanUtil.toBean(requestParam, ShortLinkCreateReqDTO.class);
            shortLinkCreateReqDTO.setOriginUrl(originUrlList.get(i));
            shortLinkCreateReqDTO.setDescription(describeList.get(i));
            try {
                ShortLinkCreateRespDTO shortlink = transactionTemplate.execute(status -> doCreateShortlink(shortLinkCreateReqDTO));
                if (shortlink != null) {
                    resultList.add(ShortLinkBaseInfoRespDTO.builder()
                            .fullShortUrl(shortlink.getFullShortUrl())
                            .originUrl(shortlink.getOriginUrl())
                            .description(describeList.get(i))
                            .build());
                }
            } catch (Exception e) {
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
        // 1. 查出旧数据 既查热库，也查冷库
        ShortLinkDO shortLinkDO = baseMapper.selectOne(Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .in(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus(),LinkEnableStatusEnum.FROZEN.getEnableStatus()));
        // 标记数据来源是否为冷库
        boolean isCold = false;
        if (shortLinkDO == null) {
            // 热库没找到，尝试去冷库找
            ShortLinkColdDO coldDO = shortLinkColdMapper.selectOne(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getGid, requestParam.getOriginGid())
                    .eq(ShortLinkColdDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkColdDO::getDelFlag, 0));
            if (coldDO != null) {
                // 找到了！转换为 ShortLinkDO 统一处理
                shortLinkDO = BeanUtil.toBean(coldDO, ShortLinkDO.class);
                isCold = true;
            }
        }
        if (shortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }
        // 2. 状态安全流转逻辑
        Integer oldStatus = shortLinkDO.getEnableStatus();
        Integer newStatus = oldStatus;
        if (Objects.equals(oldStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus())
                || Objects.equals(oldStatus, LinkEnableStatusEnum.FROZEN.getEnableStatus())) {
            if (Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType())) {
                newStatus = LinkEnableStatusEnum.ENABLE.getEnableStatus();
            } else if (requestParam.getValidDate() != null) {
                if (requestParam.getValidDate().after(new Date())) {
                    newStatus = LinkEnableStatusEnum.ENABLE.getEnableStatus();
                } else {
                    newStatus = LinkEnableStatusEnum.FROZEN.getEnableStatus();
                }
            }
        }
        boolean isOriginUrlChanged = !Objects.equals(shortLinkDO.getOriginUrl(), requestParam.getOriginUrl());
        boolean isGidChanged = !Objects.equals(shortLinkDO.getGid(), requestParam.getGid());
        // 3. 执行更新
       // 如果是 (热库数据 AND 分组没变)，则直接 Update，性能最高如果是 (热库数据 AND 分组没变)，则直接 Update，性能最高
        if (!isCold && !isGidChanged) {
            // === 情况 A：分组没变，原地更新 ===
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .set(ShortLinkDO::getOriginUrl, requestParam.getOriginUrl())
                    .set(ShortLinkDO::getDescription, requestParam.getDescription())
                    .set(ShortLinkDO::getValidDateType, requestParam.getValidDateType())
                    .set(ShortLinkDO::getEnableStatus, newStatus)
                    .set(ShortLinkDO::getValidDate,
                            Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
            baseMapper.update(null, updateWrapper);

        } else {
            // === 情况 B：分组改变 OR 数据在冷库 ===
            //引入读写锁
            RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, requestParam.getFullShortUrl()));
            RLock rLock = readWriteLock.writeLock();
            rLock.lock();
            try {
                // 3.1 删除旧路由 (区分从冷库删还是热库删)
                if (isCold) {
                    shortLinkGoToColdMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToColdDO.class)
                            .eq(ShortLinkGoToColdDO::getFullShortUrl, requestParam.getFullShortUrl())
                            .eq(ShortLinkGoToColdDO::getGid, requestParam.getOriginGid()));

                    // 3.2 删除旧详情 (冷库)
                    shortLinkColdMapper.delete(Wrappers.lambdaQuery(ShortLinkColdDO.class)
                    .eq(ShortLinkColdDO::getGid, requestParam.getOriginGid())
                     .eq(ShortLinkColdDO::getFullShortUrl,requestParam.getFullShortUrl()));
                } else {
                    shortLinkGoToMapper.delete(Wrappers.lambdaQuery(ShortLinkGoToDO.class)
                            .eq(ShortLinkGoToDO::getFullShortUrl, requestParam.getFullShortUrl())
                            .eq(ShortLinkGoToDO::getGid, requestParam.getOriginGid()));

                    // 3.2 删除旧详情 (热库)
                    baseMapper.deletePhysical(requestParam.getOriginGid(), requestParam.getFullShortUrl());
                }
                // 插新路由
                ShortLinkGoToDO shortLinkGoToDO = ShortLinkGoToDO.builder()
                        .gid(requestParam.getGid())
                        .fullShortUrl(requestParam.getFullShortUrl())
                        .build();
                shortLinkGoToMapper.insert(shortLinkGoToDO);
                // 3.2 处理主表 (ShortLink)
                // 物理删除旧数据
                baseMapper.deletePhysical(requestParam.getOriginGid(), requestParam.getFullShortUrl());
                // 3.4 插入新详情 (始终插到热库！实现回热)
                shortLinkDO.setGid(requestParam.getGid());
                shortLinkDO.setOriginUrl(requestParam.getOriginUrl());
                shortLinkDO.setDescription(requestParam.getDescription());
                shortLinkDO.setValidDateType(requestParam.getValidDateType());
                shortLinkDO.setValidDate(Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
                shortLinkDO.setEnableStatus(newStatus);
                shortLinkDO.setId(null); // ID置空，重新生成
                // 注意：这里插入的 shortLinkDO 里 favicon 还是旧的，没关系，异步线程一会儿会改它
                baseMapper.insert(shortLinkDO);
                // 更新 Redis 中的统计的今日数据
                // 逻辑：从旧分组的 ZSet 取出分数，添加到新分组 ZSet，然后从旧分组删除
                if (isGidChanged) {
                    String todayStr = DateUtil.today();
                    String fullShortUrl = requestParam.getFullShortUrl();
                    String oldGid = requestParam.getOriginGid();
                    String newGid = requestParam.getGid();
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
                                fullShortUrl,
                                String.valueOf(TimeUnit.HOURS.toSeconds(TODAY_EXPIRETIME)) // ARGV
                        );
                    }
                }
            } finally {
                rLock.unlock();
            }
        }

        // 4. 【关键步骤】如果在事务提交前发布事件，需要确保监听器逻辑正确
        if (isOriginUrlChanged) {
            eventPublisher.publishEvent(new UpdateFaviconEvent(
                    requestParam.getFullShortUrl(),
                    requestParam.getGid(),
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
        if (CharSequenceUtil.equalsAny(requestParam.getOrderTag(), OrderTagEnum.TODAY_PV.getValue(), OrderTagEnum.TODAY_UV.getValue(), OrderTagEnum.TODAY_UIP.getValue())) {
            return pageByRedisRank(requestParam);
        }

        // 场景 B：默认排序（按创建时间/总量），热库+冷库合并展示
        return pageHotColdByOrder(requestParam);
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
            long hotFallbackTotal = baseMapper.countLinkFallback(req.getGid(), todayStart);
            long coldFallbackTotal = shortLinkColdMapper.selectCount(Wrappers.<ShortLinkColdDO>lambdaQuery()
                    .eq(ShortLinkColdDO::getGid, req.getGid())
                    .in(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus(),LinkEnableStatusEnum.FROZEN.getEnableStatus())
                    .eq(ShortLinkColdDO::getDelFlag, 0)
                    .and(q -> q.lt(ShortLinkColdDO::getLastAccessTime, todayStart)
                            .or()
                            .isNull(ShortLinkColdDO::getLastAccessTime)));

            // 总条数 = Redis热数据 + DB冷数据
            long total = redisTotal + hotFallbackTotal + coldFallbackTotal;

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
                resultList.addAll(pageHotColdFallback(req.getGid(), todayStart, 0, needMore));
            }
            // 场景 C: 请求范围完全在 MySQL 内 (纯冷数据)
            else {
                // 计算 DB 的偏移量
                // 公式：当前请求的全局 offset - Redis 的总数
                long dbOffset = start - redisTotal;

                resultList.addAll(pageHotColdFallback(req.getGid(), todayStart, dbOffset, size));
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
        return pageHotColdByOrder(req, false);
    }

    /**
     * 热+冷合并分页：保持用户端默认可见，避免冷库链接“消失”
     */
    private IPage<ShortLinkPageRespDTO> pageHotColdByOrder(ShortLinkPageReqDTO req) {
        return pageHotColdByOrder(req, true);
    }

    private IPage<ShortLinkPageRespDTO> pageHotColdByOrder(ShortLinkPageReqDTO req, boolean fillToday) {
        long current = req.getCurrent();
        long size = req.getSize();
        long need = current * size;

        LambdaQueryWrapper<ShortLinkDO> hotWrapper = Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, req.getGid())
                .in(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus(),LinkEnableStatusEnum.FROZEN.getEnableStatus())
                .eq(ShortLinkDO::getDelFlag, 0);
        applyOrder(hotWrapper, req.getOrderTag());

        LambdaQueryWrapper<ShortLinkColdDO> coldWrapper = Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, req.getGid())
                .in(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus(),LinkEnableStatusEnum.FROZEN.getEnableStatus())
                .eq(ShortLinkColdDO::getDelFlag, 0);
        applyColdOrder(coldWrapper, req.getOrderTag());

        long hotTotal = baseMapper.selectCount(hotWrapper);
        long coldTotal = shortLinkColdMapper.selectCount(coldWrapper);
        long total = hotTotal + coldTotal;

        Page<ShortLinkDO> hotPage = new Page<>(1, need);
        Page<ShortLinkColdDO> coldPage = new Page<>(1, need);
        List<ShortLinkDO> hotList = baseMapper.selectPage(hotPage, hotWrapper).getRecords();
        List<ShortLinkColdDO> coldList = shortLinkColdMapper.selectPage(coldPage, coldWrapper).getRecords();

        List<ShortLinkPageRespDTO> merged = mergeHotColdList(hotList, coldList, req.getOrderTag(), fillToday);
        int fromIndex = (int) ((current - 1) * size);
        int toIndex = (int) Math.min(fromIndex + size, merged.size());
        List<ShortLinkPageRespDTO> pageRecords = fromIndex >= merged.size()
                ? new ArrayList<>()
                : merged.subList(fromIndex, toIndex);

        IPage<ShortLinkPageRespDTO> page = new Page<>();
        page.setRecords(pageRecords);
        page.setTotal(total);
        page.setCurrent(current);
        page.setSize(size);
        return page;
    }

    private void applyOrder(LambdaQueryWrapper<ShortLinkDO> wrapper, String orderTag) {
        if (CharSequenceUtil.equals(orderTag, "totalPv")) {
            wrapper.orderByDesc(ShortLinkDO::getTotalPv);
        } else if (CharSequenceUtil.equals(orderTag, "totalUv")) {
            wrapper.orderByDesc(ShortLinkDO::getTotalUv);
        } else if (CharSequenceUtil.equals(orderTag, "totalUip")) {
            wrapper.orderByDesc(ShortLinkDO::getTotalUip);
        } else {
            wrapper.orderByDesc(ShortLinkDO::getCreateTime);
        }
    }

    private void applyColdOrder(LambdaQueryWrapper<ShortLinkColdDO> wrapper, String orderTag) {
        if (CharSequenceUtil.equals(orderTag, "totalPv")) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalPv);
        } else if (CharSequenceUtil.equals(orderTag, "totalUv")) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUv);
        } else if (CharSequenceUtil.equals(orderTag, "totalUip")) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUip);
        } else {
            wrapper.orderByDesc(ShortLinkColdDO::getCreateTime);
        }
    }

    /**
     * 合并热/冷数据并按排序规则统一排序
     */
    private List<ShortLinkPageRespDTO> mergeHotColdList(List<ShortLinkDO> hotList,
                                                        List<ShortLinkColdDO> coldList,
                                                        String orderTag,
                                                        boolean fillToday) {
        List<ShortLinkPageRespDTO> merged = new ArrayList<>();
        for (ShortLinkDO hot : hotList) {
            ShortLinkPageRespDTO dto = BeanUtil.toBean(hot, ShortLinkPageRespDTO.class);
            dto.setDomain(HTTP_PROTOCOL + dto.getDomain());
            if (fillToday) {
                fillTodayStats(dto);
            } else {
                dto.setTodayPv(0);
                dto.setTodayUv(0);
                dto.setTodayUip(0);
            }
            merged.add(dto);
        }
        for (ShortLinkColdDO cold : coldList) {
            ShortLinkPageRespDTO dto = BeanUtil.toBean(cold, ShortLinkPageRespDTO.class);
            dto.setDomain(HTTP_PROTOCOL + dto.getDomain());
            if (fillToday) {
                fillTodayStats(dto);
            } else {
                dto.setTodayPv(0);
                dto.setTodayUv(0);
                dto.setTodayUip(0);
            }
            merged.add(dto);
        }

        merged.sort(buildOrderComparator(orderTag));
        return merged;
    }

    private Comparator<ShortLinkPageRespDTO> buildOrderComparator(String orderTag) {
        if (CharSequenceUtil.equals(orderTag, "totalPv")) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) -> Optional.ofNullable(dto.getTotalPv()).orElse(0)).reversed()
                    .thenComparing(dto -> Optional.ofNullable(dto.getCreateTime()).orElse(new Date(0)), Comparator.reverseOrder());
        }
        if (CharSequenceUtil.equals(orderTag, "totalUv")) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) -> Optional.ofNullable(dto.getTotalUv()).orElse(0)).reversed()
                    .thenComparing(dto -> Optional.ofNullable(dto.getCreateTime()).orElse(new Date(0)), Comparator.reverseOrder());
        }
        if (CharSequenceUtil.equals(orderTag, "totalUip")) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) -> Optional.ofNullable(dto.getTotalUip()).orElse(0)).reversed()
                    .thenComparing(dto -> Optional.ofNullable(dto.getCreateTime()).orElse(new Date(0)), Comparator.reverseOrder());
        }
        return Comparator.comparing((ShortLinkPageRespDTO dto) -> Optional.ofNullable(dto.getCreateTime()).orElse(new Date(0)), Comparator.reverseOrder());
    }

    /**
     * Redis 排行补位场景：热+冷合并，按创建时间排序补齐
     */
    private List<ShortLinkPageRespDTO> pageHotColdFallback(String gid, Date todayStart, long offset, long size) {
        long need = offset + size;
        LambdaQueryWrapper<ShortLinkDO> hotWrapper = Wrappers.<ShortLinkDO>lambdaQuery()
                .eq(ShortLinkDO::getGid, gid)
                .in(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus(),LinkEnableStatusEnum.FROZEN.getEnableStatus())
                .eq(ShortLinkDO::getDelFlag, 0)
                .and(q -> q.lt(ShortLinkDO::getLastAccessTime, todayStart)
                        .or()
                        .isNull(ShortLinkDO::getLastAccessTime))
                .orderByDesc(ShortLinkDO::getCreateTime);
        LambdaQueryWrapper<ShortLinkColdDO> coldWrapper = Wrappers.<ShortLinkColdDO>lambdaQuery()
                .eq(ShortLinkColdDO::getGid, gid)
                .in(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus(),LinkEnableStatusEnum.FROZEN.getEnableStatus())
                .eq(ShortLinkColdDO::getDelFlag, 0)
                .and(q -> q.lt(ShortLinkColdDO::getLastAccessTime, todayStart)
                        .or()
                        .isNull(ShortLinkColdDO::getLastAccessTime))
                .orderByDesc(ShortLinkColdDO::getCreateTime);

        Page<ShortLinkDO> hotPage = new Page<>(1, need);
        Page<ShortLinkColdDO> coldPage = new Page<>(1, need);
        List<ShortLinkDO> hotList = baseMapper.selectPage(hotPage, hotWrapper).getRecords();
        List<ShortLinkColdDO> coldList = shortLinkColdMapper.selectPage(coldPage, coldWrapper).getRecords();

        List<ShortLinkPageRespDTO> merged = mergeHotColdList(hotList, coldList, null, false);
        int fromIndex = (int) offset;
        int toIndex = (int) Math.min(fromIndex + size, merged.size());
        if (fromIndex >= merged.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(merged.subList(fromIndex, toIndex));
    }

    // 辅助方法：Redis 结果组装
    private List<ShortLinkPageRespDTO> buildResultByUrls(Set<String> urls, String gid) {
        if (urls == null || urls.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 批量查 DB 基础信息
        List<ShortLinkDO> linkDOList = baseMapper.selectList(Wrappers.<ShortLinkDO>lambdaQuery()
                .in(ShortLinkDO::getFullShortUrl, urls)
                .eq(ShortLinkDO::getGid, gid)
                .eq(ShortLinkDO::getDelFlag, 0));

        // 2. 内存重排序
        Map<String, ShortLinkDO> linkMap = linkDOList.stream()
                .collect(Collectors.toMap(ShortLinkDO::getFullShortUrl, Function.identity()));

        // 3. 冷库兜底补充
        List<String> missingUrls = urls.stream()
                .filter(url -> !linkMap.containsKey(url))
                .toList();
        Map<String, ShortLinkColdDO> coldMap = new HashMap<>();
        if (!missingUrls.isEmpty()) {
            List<ShortLinkColdDO> coldList = shortLinkColdMapper.selectList(Wrappers.<ShortLinkColdDO>lambdaQuery()
                    .in(ShortLinkColdDO::getFullShortUrl, missingUrls)
                    .eq(ShortLinkColdDO::getGid, gid)
                    .eq(ShortLinkColdDO::getDelFlag, 0));
            coldMap = coldList.stream()
                    .collect(Collectors.toMap(ShortLinkColdDO::getFullShortUrl, Function.identity()));
        }

        List<ShortLinkPageRespDTO> list = new ArrayList<>();
        String todayStr = DateUtil.today();
        String pvKey = String.format(RANK_KEY, OrderTagEnum.TODAY_PV.getValue(), gid, todayStr);
        String uvKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UV.getValue(), gid, todayStr);
        String uipKey = String.format(RANK_KEY, OrderTagEnum.TODAY_UIP.getValue(), gid, todayStr);
        for (String url : urls) {
            ShortLinkDO dbLink = linkMap.get(url);
            if (dbLink != null) {
                fillShortLinkStats(BeanUtil.toBean(dbLink, ShortLinkPageRespDTO.class), pvKey, url, uvKey, uipKey, list);
                continue;
            }
            ShortLinkColdDO coldLink = coldMap.get(url);
            if (coldLink != null) {
                fillShortLinkStats(BeanUtil.toBean(coldLink, ShortLinkPageRespDTO.class), pvKey, url, uvKey, uipKey, list);
            }
        }
        return list;
    }

    /*
    *  填充短链接统计信息
    * */
    private void fillShortLinkStats(ShortLinkPageRespDTO coldLink, String pvKey, String url, String uvKey, String uipKey, List<ShortLinkPageRespDTO> list) {
        coldLink.setDomain(HTTP_PROTOCOL + coldLink.getDomain());
        Double pv = stringRedisTemplate.opsForZSet().score(pvKey, url);
        coldLink.setTodayPv(pv == null ? 0 : pv.intValue());
        Double uv = stringRedisTemplate.opsForZSet().score(uvKey, url);
        coldLink.setTodayUv(uv == null ? 0 : uv.intValue());
        Double uip = stringRedisTemplate.opsForZSet().score(uipKey, url);
        coldLink.setTodayUip(uip == null ? 0 : uip.intValue());
        list.add(coldLink);
    }

    /**
     * 从 Redis ZSet 中获取今日实时统计数据
     */
    @Override
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
                .in(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus(),LinkEnableStatusEnum.FROZEN.getEnableStatus())
                .eq(ShortLinkDO::getDelFlag,0)
                .in(ShortLinkDO::getGid, requestParam)
                .groupBy(ShortLinkDO::getGid);
        List<ShortLinkGroupCountRespDTO> hotCounts = shortLinkMapper.selectGroupCount(queryWrapper);
        // 冷库数量统计
        LambdaQueryWrapper<ShortLinkColdDO> coldWrapper = Wrappers.lambdaQuery(ShortLinkColdDO.class)
                .in(ShortLinkColdDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus(),LinkEnableStatusEnum.FROZEN.getEnableStatus())
                .eq(ShortLinkColdDO::getDelFlag,0)
                .in(ShortLinkColdDO::getGid, requestParam)
                .groupBy(ShortLinkColdDO::getGid);
        List<ShortLinkGroupCountRespDTO> coldCounts = shortLinkColdMapper.selectGroupCount(coldWrapper);

        Map<String, Long> countMap = new HashMap<>();
        for (ShortLinkGroupCountRespDTO dto : hotCounts) {
            countMap.put(dto.getGid(), dto.getShortLinkCount());
        }
        for (ShortLinkGroupCountRespDTO dto : coldCounts) {
            countMap.merge(dto.getGid(), dto.getShortLinkCount(), Long::sum);
        }

        List<ShortLinkGroupCountRespDTO> merged = new ArrayList<>();
        for (String gid : requestParam) {
            Long count = countMap.get(gid);
            if (count != null) {
                merged.add(ShortLinkGroupCountRespDTO.builder()
                        .gid(gid)
                        .shortLinkCount(count)
                        .build());
            }
        }
        return merged;
    }

    /*
    * 生成短链接后缀
    * */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        int customGenerateCount = 0;
        String shortUri;
        StringBuilder originUrl = new StringBuilder(requestParam.getOriginUrl());
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接生成频繁，请稍后再试");
            }
            shortUri = HashUtil.hashToBase62(originUrl);
            if(!shortlinkUriCreateCachePenetrationBloomFilter.contains(defaultDomain + "/" + shortUri)){
                break;
            }
            originUrl.append(UUID.randomUUID());
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
            StringBuilder originUrl = new StringBuilder(requestParam.getOriginUrl());
            originUrl.append(UUID.randomUUID());
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
        if(CharSequenceUtil.isBlank(domain)) {
            throw new ClientException("跳转链接填写错误");
        }
        final List<String> details = gotoDomainWhiteListConfiguration.getDetails();
        if(!details.contains(domain)) {
            throw new ClientException("请生成以下网站跳转链接：" + gotoDomainWhiteListConfiguration.getNames());
        }
    }
}
