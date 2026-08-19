# Phase 2 实施计划：Redirect 跳转服务

> 基于设计文档: `docs/plans/2026-02-16-project-service-refactor-design.md`
> 分支: `feature/refactor-project`
> 前置: Phase 1 已完成（Core CRUD + MQ 统一）

## 概述

将旧模块 `ShortLinkServiceImpl` 中的跳转逻辑（`redirect` + 7 个私有方法 + 统计埋点）提取为独立的 `ShortLinkRedirectService`，复用已有的 `ShortLinkCacheHelper`，新增统计事件 MQ 生产者。

## 现状分析

### 已有基础（Phase 1 产出）
- `ShortLinkCacheHelper`：warmUp / evictLocalCache / bloomFilter 操作
- `ShortLinkCacheProducer`：广播缓存失效消息
- `RedisKeyConstant`：GOTO_SHORT_LINK_KEY、GOTO_IS_NULL_SHORT_LINK_KEY、LOCK_GOTO_SHORT_LINK_KEY
- DAO 层：ShortLinkMapper、ShortLinkGoToMapper、ShortLinkColdMapper、ShortLinkGoToColdMapper
- Caffeine 本地缓存 Bean（`ShortLinkLocalCacheConfig`）

### 需要新增
- `ShortLinkRedirectService` 接口 + 实现
- `ShortLinkRedirectController`（`GET /{short-uri}` 端点）
- `ShortLinkStatsRecordEvent` 统计事件 DTO
- `ShortLinkStatsProducer` 统计 MQ 生产者
- `RocketMQConstant` 新增统计 Topic/Group
- `RedisKeyConstant` 新增 UV Set Key
- `ShortLinkConstant` 新增 Cookie 有效期常量、404 页面常量
- `LinkUtil` 补充 redirect 相关工具方法（getActualIp、getOs、getBrowser、getDevice、getNetwork）
- `ShortLinkService` 接口新增 `redirect` 方法
- `ShortLinkServiceImpl` Facade 新增 redirect 委托

### 设计决策

| 决策项 | 结论 | 理由 |
|--------|------|------|
| 是否使用 frameworks/cache MultistageCache | 否，保持手动管理 | 旧代码的 `parseCache` 有业务逻辑（有效期校验、智能续期），MultistageCache 的 `multiGet` 无法承载这些自定义逻辑 |
| 缓存值格式 | `"validTimeStamp\|originUrl\|gid"` | 与 Phase 1 的 `warmUp` 保持一致，避免 DB 查询 |
| 统计事件发送方式 | RocketMQ 异步 | 不阻塞跳转主流程 |
| 冷库查询 | 热表优先，冷表兜底 | 与旧代码一致 |

---

## Step 1: 补充常量

### 1.1 RocketMQConstant — 新增统计 Topic/Group

**文件**: `common/constant/RocketMQConstant.java`（编辑）

```java
/** 统计数据记录 Topic */
public static final String STATS_RECORD_TOPIC = "short_link_project_stats_record_topic";

/** 统计数据记录消费者组（供 stats-service 使用） */
public static final String STATS_RECORD_GROUP = "short_link_project_stats_record_group";
```

### 1.2 RedisKeyConstant — 新增 UV Set Key

**文件**: `common/constant/RedisKeyConstant.java`（编辑）

```java
/** 短链接 UV 去重 Set Key */
public static final String SHORT_LINK_STATS_UV_KEY = "short-link:stats:uv:";
```

### 1.3 ShortLinkConstant — 新增 Cookie 和 404 常量

**文件**: `common/constant/ShortLinkConstant.java`（编辑）

```java
/** UV Cookie 有效期（秒），30 天 */
public static final int DEFAULT_COOKIE_VALID_TIME = 60 * 60 * 24 * 30;

/** 短链接不存在时的跳转页面 */
public static final String PAGE_NOT_FOUND = "/page/notfound";
```

---

## Step 2: 补充 LinkUtil 工具方法

**文件**: `toolkit/LinkUtil.java`（编辑）

从旧模块 `project/src/.../toolkit/LinkUtil.java` 搬迁以下 5 个静态方法：

| 方法 | 用途 |
|------|------|
| `getActualIp(HttpServletRequest)` | 从多级代理头中提取真实 IP |
| `getOs(HttpServletRequest)` | 从 User-Agent 解析操作系统 |
| `getBrowser(HttpServletRequest)` | 从 User-Agent 解析浏览器（依赖 hutool UserAgentUtil） |
| `getDevice(HttpServletRequest)` | 判断 Mobile / PC |
| `getNetwork(HttpServletRequest)` | 判断 WIFI / Mobile 网络 |

**注意**: 当前 `LinkUtil` 仅有 `getLinkCacheValidTime` 和 `extractDomain`，需追加上述方法。无需修改已有方法。

---

## Step 3: 创建 ShortLinkStatsRecordEvent

**文件**: `mq/event/ShortLinkStatsRecordEvent.java`（新建）

从旧模块搬迁，字段与旧代码完全一致：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsRecordEvent {
    private String fullShortUrl;
    private String remoteAddr;
    private String os;
    private String browser;
    private String device;
    private String network;
    private String uv;
    private String gid;
    private Date currentDate;
    private String eventId;
}
```

---

## Step 4: 创建 ShortLinkStatsProducer

**文件**: `mq/producer/ShortLinkStatsProducer.java`（新建）

继承 `AbstractCommonSendProduceTemplate`，异步发送统计事件：

```java
@Slf4j
@Component
public class ShortLinkStatsProducer extends AbstractCommonSendProduceTemplate<ShortLinkStatsRecordEvent> {

    public ShortLinkStatsProducer(RocketMQTemplate rocketMQTemplate) {
        super(rocketMQTemplate);
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendParam(ShortLinkStatsRecordEvent event) {
        return BaseSendExtendDTO.builder()
                .topic(RocketMQConstant.STATS_RECORD_TOPIC)
                .keys(event.getFullShortUrl())
                .sendType(BaseSendExtendDTO.SendType.ASYNC)
                .build();
    }
}
```

---

## Step 5: 创建 ShortLinkRedirectService 接口

**文件**: `service/ShortLinkRedirectService.java`（新建）

```java
public interface ShortLinkRedirectService {
    void redirect(String shortUri, ServletRequest request, ServletResponse response);
}
```

---

## Step 6: 创建 ShortLinkRedirectServiceImpl

**文件**: `service/impl/ShortLinkRedirectServiceImpl.java`（新建）

**核心**: 从旧模块 `ShortLinkServiceImpl` 搬迁 `redirect` 方法及其全部私有辅助方法。

### 依赖注入

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkRedirectServiceImpl implements ShortLinkRedirectService {

    private final ShortLinkGoToMapper shortLinkGoToMapper;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkColdMapper shortLinkColdMapper;
    private final ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ShortLinkStatsProducer statsProducer;
    private final ShortLinkCacheHelper cacheHelper;
    private final Cache<String, String> shortLinkCache;  // Caffeine L1

    @Value("${short-link.domain.default}")
    private String defaultDomain;
```

### 搬迁方法清单

从旧模块 `main:project/.../ShortLinkServiceImpl.java` 逐方法搬迁：

| 方法 | 旧代码行号 | 改动点 |
|------|-----------|--------|
| `redirect()` | 130-148 | 入口方法，无改动 |
| `getFromCache()` | 153-178 | 使用已有 `shortLinkCache` Bean，无改动 |
| `parseCache()` | 393-430 | 解析缓存字符串 + 有效期校验 + 智能续期，无改动 |
| `isPossiblePenetration()` | 180-190 | 布隆过滤器改为 `cacheHelper.bloomFilterContains()`，空值缓存判断保留 |
| `processWithLock()` | 193-228 | 分布式锁 + 双重检查，无改动 |
| `loadFromDb()` | 230-270 | 热表→冷表查询，无改动 |
| `rebuildCache()` | 321-330 | 写 L1 + L2 缓存，无改动 |
| `isNotExpired()` | 335-337 | 判断有效期，无改动 |
| `executeRedirect()` | 313-316 | 发送统计事件改为 `statsProducer.send()`，然后 302 重定向 |
| `buildLinkStatsRecordDTO()` | 347-388 | 构造统计事件，使用新的 `LinkUtil` 工具方法 |

### `isPossiblePenetration` 改动

原代码：
```java
if (!shortlinkUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
    return true;
}
```

替换为：
```java
if (!cacheHelper.bloomFilterContains(fullShortUrl)) {
    return true;
}
```

### 内部类 ShortLinkCacheObj

从旧代码搬迁，作为 `ShortLinkRedirectServiceImpl` 的私有内部类：

```java
@lombok.Data
@lombok.AllArgsConstructor
private static class ShortLinkCacheObj {
    private String originUrl;
    private String gid;
    private Date validDate;
}
```

---

## Step 7: 更新 Facade 层

### 7.1 ShortLinkService 接口新增 redirect 方法

**文件**: `service/ShortLinkService.java`（编辑）

```java
/** 短链接跳转 */
void redirect(String shortUri, ServletRequest request, ServletResponse response);
```

### 7.2 ShortLinkServiceImpl 新增 redirect 委托

**文件**: `service/impl/ShortLinkServiceImpl.java`（编辑）

新增依赖注入 `ShortLinkRedirectService redirectService`，新增委托方法：

```java
@Override
public void redirect(String shortUri, ServletRequest request, ServletResponse response) {
    redirectService.redirect(shortUri, request, response);
}
```

---

## Step 8: 创建 ShortLinkRedirectController

**文件**: `controller/ShortLinkRedirectController.java`（新建）

独立控制器，与 `ShortLinkController` 分离（跳转是高频路径，职责不同）：

```java
@RestController
@RequiredArgsConstructor
public class ShortLinkRedirectController {

    private final ShortLinkService shortLinkService;

    @GetMapping("/{short-uri}")
    public void redirect(@PathVariable("short-uri") String shortUri,
                         ServletRequest request, ServletResponse response) {
        shortLinkService.redirect(shortUri, request, response);
    }
}
```

**注意**: 旧代码有 `@SentinelResource` 限流注解，本次暂不迁移（Sentinel 依赖未引入），后续按需添加。

---

## Step 9: 验证编译

**命令**: `mvn compile -pl services/project-service -am -DskipTests`

**验证点**:
- 无编译错误
- `ShortLinkRedirectServiceImpl` 正确注入所有依赖
- `ShortLinkStatsProducer` 继承模板正确
- `LinkUtil` 新增方法无缺失导入（hutool UserAgentUtil）

---

## 执行顺序

```
Step 1 (常量) → Step 2 (LinkUtil) → Step 3 (StatsEvent) → Step 4 (StatsProducer)
    → Step 5 (接口) → Step 6 (RedirectServiceImpl)
    → Step 7 (Facade更新) → Step 8 (Controller) → Step 9 (编译验证)
```

Step 1-4 无相互依赖，可并行。Step 5-6 依赖 Step 1-4。Step 7 依赖 Step 5-6。Step 8 依赖 Step 7。Step 9 最后执行。

---

## 文件变更清单

| 操作 | 文件路径 |
|------|---------|
| 编辑 | `common/constant/RocketMQConstant.java` |
| 编辑 | `common/constant/RedisKeyConstant.java` |
| 编辑 | `common/constant/ShortLinkConstant.java` |
| 编辑 | `toolkit/LinkUtil.java` |
| 新建 | `mq/event/ShortLinkStatsRecordEvent.java` |
| 新建 | `mq/producer/ShortLinkStatsProducer.java` |
| 新建 | `service/ShortLinkRedirectService.java` |
| 新建 | `service/impl/ShortLinkRedirectServiceImpl.java` |
| 编辑 | `service/ShortLinkService.java` |
| 编辑 | `service/impl/ShortLinkServiceImpl.java` |
| 新建 | `controller/ShortLinkRedirectController.java` |

所有路径相对于 `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/`

---

## 风险点

| 风险 | 应对 |
|------|------|
| hutool `UserAgentUtil` 依赖缺失 | 检查 pom.xml 是否已有 hutool-all，Phase 1 已使用 hutool DateUtil 确认存在 |
| 统计消费者不在本 Phase 范围 | StatsProducer 只负责发送，消费者在 Phase 4 stats-service 中实现 |
| Sentinel 限流未迁移 | 本次不引入 Sentinel 依赖，后续按需添加 |
