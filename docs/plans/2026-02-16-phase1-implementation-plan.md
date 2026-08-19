# Phase 1 实施计划：Core CRUD + MQ 统一

> 基于设计文档: `docs/plans/2026-02-16-project-service-refactor-design.md`
> 分支: `feature/refactor-project`

## 概述

将现有 677 行的 `ShortLinkServiceImpl` 拆分为 Facade + CoreService 模式，提取 CacheHelper 工具类，实现 MQ 消费者。

## 前置条件

- 当前 `ShortLinkServiceImpl` 已包含完整的 CRUD 逻辑（创建、批量创建、修改、分页查询、分组统计、今日统计填充）
- MQ 生产者已就绪（`ShortLinkCacheProducer`、`ShortLinkRiskProducer`、`ShortLinkExpireArchiveProducer`）
- DAO 层、DTO 层、配置类已完成

---

## Step 1: 补充 RocketMQ 消费者组常量

**文件**: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/common/constant/RocketMQConstant.java`

**操作**: 编辑现有文件，新增消费者组常量（参考旧模块 `project/src/.../RocketMQConstant.java`）

**新增内容**:
```java
/** 清除本地缓存消费者组 */
public static final String CACHE_INVALIDATE_GROUP = "short_link_project_cache_invalidate_group";

/** AI 风控审核消费者组 */
public static final String RISK_CHECK_GROUP = "short_link_project_risk_check_group";

/** 过期短链归档消费者组 */
public static final String EXPIRE_ARCHIVE_GROUP = "short_link_project_expire_archive_group";
```

---

## Step 2: 创建 ShortLinkCacheHelper 缓存工具类

**文件**: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/helper/ShortLinkCacheHelper.java`（新建）

**操作**: 新建文件

**职责**: 封装 Caffeine 本地缓存 + Redis 缓存的读写操作，供 CoreService 和未来的 RedirectService 共用。

**从 `ShortLinkServiceImpl` 提取的逻辑**:
- 缓存预热（第 147-152 行的 Redis set 逻辑）
- 空值缓存删除（第 154 行）
- 布隆过滤器操作（第 156 行）
- 本地缓存失效（参考旧模块 `ShortLinkCacheRocketMQConsumer` 的 invalidate 逻辑）

**实现要点**:
```java
@Component
@RequiredArgsConstructor
public class ShortLinkCacheHelper {

    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<String, String> shortLinkCache;
    private final RBloomFilter<String> shortlinkUriCreateCachePenetrationBloomFilter;

    /** 缓存预热：写入 Redis + 删除空值缓存 + 加入布隆过滤器 */
    public void warmUp(String fullShortUrl, String originUrl, String gid, Date validDate) {
        // 构造 cacheValue: "validTimeStamp|originUrl|gid"
        // 计算 TTL: LinkUtil.getLinkCacheValidTime(validDate)
        // stringRedisTemplate.opsForValue().set(GOTO_SHORT_LINK_KEY, cacheValue, ttl, MILLISECONDS)
        // stringRedisTemplate.delete(GOTO_IS_NULL_SHORT_LINK_KEY)
        // shortlinkUriCreateCachePenetrationBloomFilter.add(fullShortUrl)
    }

    /** 清除本地 Caffeine 缓存 */
    public void evictLocalCache(String fullShortUrl) {
        // shortLinkCache.invalidate(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl))
        // shortLinkCache.invalidate(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl))
    }

    /** 添加到布隆过滤器 */
    public void addToBloomFilter(String fullShortUrl) {
        // shortlinkUriCreateCachePenetrationBloomFilter.add(fullShortUrl)
    }

    /** 检查布隆过滤器 */
    public boolean bloomFilterContains(String fullShortUrl) {
        // return shortlinkUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)
    }
}
```

---

## Step 3: 创建 ShortLinkCoreService 接口

**文件**: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/service/ShortLinkCoreService.java`（新建）

**操作**: 新建文件

**方法签名**（与现有 `ShortLinkService` 中的 CRUD 方法一致）:
```java
public interface ShortLinkCoreService {

    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam);

    void updateShortLink(ShortLinkUpdateReqDTO requestParam);

    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

    List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam);

    void fillTodayStats(ShortLinkPageRespDTO requestParam);
}
```

---

## Step 4: 创建 ShortLinkCoreServiceImpl 实现

**文件**: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/service/impl/ShortLinkCoreServiceImpl.java`（新建）

**操作**: 新建文件

**核心**: 将 `ShortLinkServiceImpl` 的全部业务逻辑搬迁到此类。

**依赖注入**:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkCoreServiceImpl implements ShortLinkCoreService {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ShortLinkRiskProducer riskProducer;
    private final ShortLinkCacheProducer cacheProducer;
    private final ShortLinkCacheHelper cacheHelper;  // ← 新增：使用 CacheHelper
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final PlatformTransactionManager transactionManager;
    private final DefaultRedisScript<Long> statsRankMigrateScript;

    @Value("${short-link.domain.default}")
    private String defaultDomain;

    @Value("${short-link.create.strategy}")
    private String createStrategy;
```

**搬迁方法清单**（从 `ShortLinkServiceImpl` 逐方法搬迁）:

| 方法 | 源文件行号 | 改动点 |
|------|-----------|--------|
| `createShortLink()` | 116-173 | 缓存预热改为调用 `cacheHelper.warmUp()`，布隆过滤器改为 `cacheHelper.addToBloomFilter()` |
| `batchCreateShortLink()` | 176-203 | 无改动，内部调用 `createShortLink()` |
| `updateShortLink()` | 207-283 | 无改动 |
| `pageShortLink()` | 286-297 | 无改动 |
| `pageByRedisRank()` | 302-365 | 无改动（私有方法一起搬迁） |
| `fallbackToBaseQuery()` | 370-372 | 无改动 |
| `pageHotColdByOrder()` | 377-434 | 无改动 |
| `applyOrder()` | 439-449 | 无改动 |
| `applyColdOrder()` | 454-464 | 无改动 |
| `mergeHotColdList()` | 469-507 | 无改动 |
| `buildOrderComparator()` | 512-533 | 无改动 |
| `pageHotColdFallback()` | 538-580 | 无改动 |
| `buildResultByUrls()` | 585-626 | 无改动 |
| `listGroupShortLinkCount()` | 629-645 | 无改动 |
| `fillTodayStats()` | 648-661 | 无改动 |
| `verificationWhitelist()` | 663-676 | 无改动 |

**`createShortLink()` 具体改动**:

原代码（第 147-156 行）:
```java
// 缓存预热
long validTimeStamp = (shortLinkDO.getValidDate() != null) ? shortLinkDO.getValidDate().getTime() : -1;
String cacheValue = String.format("%d|%s|%s", validTimeStamp, shortLinkDO.getOriginUrl(), shortLinkDO.getGid());
long initialTTL = LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate());
stringRedisTemplate.opsForValue().set(
        String.format(GOTO_SHORT_LINK_KEY, shortLinkDO.getFullShortUrl()),
        cacheValue, initialTTL, TimeUnit.MILLISECONDS);
// 删除空值缓存
stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
// 加入布隆过滤器
shortlinkUriCreateCachePenetrationBloomFilter.add(shortLinkDO.getFullShortUrl());
```

替换为:
```java
// 缓存预热
cacheHelper.warmUp(shortLinkDO.getFullShortUrl(), shortLinkDO.getOriginUrl(),
        shortLinkDO.getGid(), shortLinkDO.getValidDate());
```

原代码（第 141-143 行 DuplicateKeyException catch 中）:
```java
if (!shortlinkUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
    shortlinkUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
}
```

替换为:
```java
if (!cacheHelper.bloomFilterContains(fullShortUrl)) {
    cacheHelper.addToBloomFilter(fullShortUrl);
}
```

**注意**: `ShortLinkCoreServiceImpl` 不继承 `ServiceImpl<ShortLinkMapper, ShortLinkDO>`，因为 Facade 层的 `ShortLinkServiceImpl` 会保留这个继承关系。CoreService 直接注入 Mapper 操作。

---

## Step 5: 重构 ShortLinkServiceImpl 为 Facade

**文件**: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/service/impl/ShortLinkServiceImpl.java`

**操作**: 编辑现有文件，将 677 行的实现替换为纯委托模式

**重构后内容**:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final ShortLinkCoreService coreService;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        return coreService.createShortLink(requestParam);
    }

    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        return coreService.batchCreateShortLink(requestParam);
    }

    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        coreService.updateShortLink(requestParam);
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return coreService.pageShortLink(requestParam);
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        return coreService.listGroupShortLinkCount(requestParam);
    }

    @Override
    public void fillTodayStats(ShortLinkPageRespDTO requestParam) {
        coreService.fillTodayStats(requestParam);
    }
}
```

**关键**: 保留 `extends ServiceImpl<ShortLinkMapper, ShortLinkDO>` 以维持 `IService` 契约（其他模块可能通过 `ShortLinkService` 调用 `getOne()`、`update()` 等 MyBatis-Plus 基础方法）。

---

## Step 6: 实现 ShortLinkCacheConsumer（缓存失效消费者）

**文件**: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/mq/consumer/ShortLinkCacheConsumer.java`（新建）

**操作**: 新建文件

**参考**: 旧模块 `ShortLinkCacheRocketMQConsumer`（广播模式，清除本地 Caffeine 缓存）

**实现**:
```java
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConstant.CACHE_INVALIDATE_TOPIC,
        consumerGroup = RocketMQConstant.CACHE_INVALIDATE_GROUP,
        selectorExpression = RocketMQConstant.CACHE_INVALIDATE_TAG,
        messageModel = MessageModel.BROADCASTING
)
public class ShortLinkCacheConsumer implements RocketMQListener<String> {

    private final ShortLinkCacheHelper cacheHelper;

    @Override
    public void onMessage(String fullShortUrl) {
        log.info("[MQ广播] 接收到缓存清除消息，目标：{}", fullShortUrl);
        cacheHelper.evictLocalCache(fullShortUrl);
    }
}
```

---

## Step 7: 实现 ShortLinkExpireArchiveConsumer（过期归档消费者）

**文件**: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/mq/consumer/ShortLinkExpireArchiveConsumer.java`（新建）

**操作**: 新建文件

**参考**: 旧模块 `ShortLinkExpireArchiveRocketMQConsumer`（284 行，包含冻结 + 归档两阶段处理）

**关键差异**:
1. 旧模块使用 `MessageQueueIdempotentHandler`（自定义幂等处理器），新模块使用 `frameworks/idempotent` 的 `@Idempotent` 注解
2. 旧模块依赖 `ShortLinkHistoryMapper`（历史库），新模块暂不迁移历史库逻辑（Phase 3 冷热数据优化时处理）
3. 旧模块依赖 `UserNotificationService`（用户通知），新模块暂不迁移（Phase 4 独立微服务时处理）

**Phase 1 简化实现**（仅处理冻结阶段，归档和通知留给后续 Phase）:
```java
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConstant.EXPIRE_ARCHIVE_TOPIC,
        consumerGroup = RocketMQConstant.EXPIRE_ARCHIVE_GROUP
)
public class ShortLinkExpireArchiveConsumer implements RocketMQListener<ShortLinkExpireArchiveEvent> {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkCacheProducer cacheProducer;
    private final ShortLinkExpireArchiveProducer expireArchiveProducer;
    private final ShortLinkCacheHelper cacheHelper;

    @Value("${short-link.expire.grace-days:30}")
    private int graceDays;

    @Override
    @Idempotent(
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            key = "#event.eventId",
            uniqueKeyPrefix = "expire-archive:",
            keyTimeout = 7200
    )
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ShortLinkExpireArchiveEvent event) {
        ShortLinkExpireArchiveEvent.Stage stage = event.getStage();
        if (stage == null) {
            stage = ShortLinkExpireArchiveEvent.Stage.FREEZE;
        }
        if (stage == ShortLinkExpireArchiveEvent.Stage.FREEZE) {
            handleFreeze(event);
        }
        // ARCHIVE 阶段留给 Phase 3 实现
    }

    // handleFreeze() 逻辑从旧模块搬迁：
    // 1. 查询热库/冷库中的链接
    // 2. 检查是否真的过期
    // 3. 更新 enableStatus = FROZEN
    // 4. 投递 ARCHIVE 阶段延迟消息（graceDays 后触发）
    // 5. 清除缓存
}
```

**注意**: `@Idempotent` 注解替代了旧模块的手动 `MessageQueueIdempotentHandler` 调用。需要确认 `frameworks/idempotent` 模块已在 pom.xml 中引入。

---

## Step 8: 添加 idempotent 框架依赖

**文件**: `services/project-service/pom.xml`

**操作**: 编辑现有文件，在 `<dependencies>` 中新增

```xml
<dependency>
    <groupId>com.xhy.shortlink</groupId>
    <artifactId>shortlink-idempotent-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## Step 9: 验证编译

**命令**: `mvn compile -pl services/project-service -am -DskipTests`

**验证点**:
- 无编译错误
- `ShortLinkServiceImpl` 成功委托给 `ShortLinkCoreService`
- `ShortLinkCacheHelper` 被 CoreService 和 Consumer 正确注入
- MQ 消费者注解配置正确

---

## 执行顺序

```
Step 1 (常量) → Step 2 (CacheHelper) → Step 3 (接口) → Step 4 (CoreServiceImpl)
    → Step 5 (Facade重构) → Step 6 (CacheConsumer) → Step 7 (ExpireArchiveConsumer)
    → Step 8 (pom依赖) → Step 9 (编译验证)
```

Step 1-3 无依赖，可并行。Step 4 依赖 Step 2-3。Step 5 依赖 Step 4。Step 6-7 依赖 Step 1-2。Step 8 独立。Step 9 最后执行。

## 文件变更清单

| 操作 | 文件路径 |
|------|---------|
| 编辑 | `common/constant/RocketMQConstant.java` |
| 新建 | `helper/ShortLinkCacheHelper.java` |
| 新建 | `service/ShortLinkCoreService.java` |
| 新建 | `service/impl/ShortLinkCoreServiceImpl.java` |
| 编辑 | `service/impl/ShortLinkServiceImpl.java` |
| 新建 | `mq/consumer/ShortLinkCacheConsumer.java` |
| 新建 | `mq/consumer/ShortLinkExpireArchiveConsumer.java` |
| 编辑 | `pom.xml` |

所有路径相对于 `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/`
