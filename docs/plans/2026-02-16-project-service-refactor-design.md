# Project Service 重构设计文档

> 日期: 2026-02-16
> 分支: feature/refactor-project
> 范围: 全链路微服务重构（project、stats、risk、user、gateway）
> 策略: 新旧并存，旧模块保留作为回退方案

## 1. 决策摘要

| 决策项 | 结论 |
|--------|------|
| 拆分策略 | 混合方案：按业务域拆分 + 缓存作为工具类 |
| MQ 方案 | 统一 RocketMQ，移除 Redis Stream |
| Stats/RiskControl | 独立 Maven 微服务模块（services/stats-service、services/risk-service） |
| Redirect | 单独阶段实现，作为独立服务类 |
| 回收站 | 后续再做，本次不迁移 |
| Admin → User-Service | 补全 Feign 代理层，拆分 Feign Client（project + stats） |
| Gateway | 迁移至 services/gateway-service，统一模块管理 |
| 旧模块处理 | 暂时保留旧 admin/ 和 gateway/，不删除 |
| Aggregation | 最后适配 |
| 优先级 | 严格 P0→P1→P2 |
| Frameworks | 充分复用 frameworks/ 中的组件模块 |

## 2. 目标架构

### 2.1 服务拆分总览

```
services/
├── user-service/             # 用户服务 (Port 8002)
│   ├── controller/
│   │   ├── UserController.java
│   │   ├── GroupController.java
│   │   ├── ShortLinkController.java          # Feign 代理 → project-service
│   │   ├── ShortLinkStatsController.java     # Feign 代理 → stats-service
│   │   ├── RecycleBinController.java         # Feign 代理 → project-service
│   │   └── UrlTitleController.java           # Feign 代理 → project-service
│   └── remote/
│       ├── ShortLinkRemoteService.java       # Feign → project-service
│       └── ShortLinkStatsRemoteService.java  # Feign → stats-service
│
├── project-service/          # 短链接核心服务 (Port 8001)
│   ├── service/
│   │   ├── ShortLinkService.java              # Facade 接口（保持不变）
│   │   ├── ShortLinkServiceImpl.java          # Facade 实现（委托给子服务）
│   │   ├── ShortLinkCoreService.java          # 核心 CRUD
│   │   ├── ShortLinkRedirectService.java      # 跳转重定向
│   │   └── ShortLinkColdDataService.java      # 冷热数据管理
│   ├── helper/
│   │   └── ShortLinkCacheHelper.java          # 缓存工具类
│   ├── mq/
│   │   ├── producer/                          # RocketMQ 生产者（已完成）
│   │   └── consumer/                          # RocketMQ 消费者（待实现）
│   └── job/
│       └── ShortLinkColdMigrationJob.java     # 冷数据迁移定时任务
│
├── stats-service/            # 统计服务（独立微服务）
│   ├── service/
│   │   ├── ShortLinkStatsService.java
│   │   └── LinkAccessStatsService.java
│   ├── mq/consumer/
│   │   └── ShortLinkStatsSaveConsumer.java
│   └── controller/
│       └── ShortLinkStatsController.java
│
└── risk-service/             # 风控服务（独立微服务）
    ├── service/
    │   ├── UrlRiskControlService.java
    │   └── UserNotificationService.java
    ├── mq/consumer/
    │   └── ShortLinkRiskConsumer.java
    └── controller/
        └── RiskControlController.java

├── gateway-service/          # API 网关 (Port 8000)
│   ├── filter/
│   │   └── TokenValidateGatewayFilterFactory.java
│   └── config/
│       └── Config.java
```

### 2.2 Frameworks 模块复用计划

| Framework 模块 | 用途 | 使用方 |
|---------------|------|--------|
| **cache** | MultistageCache 多级缓存（L1 Caffeine + L2 Redis）、布隆过滤器防穿透、分布式锁防击穿 | RedirectService、CacheHelper |
| **database** | BaseDO 基类、ShardingSphere 分片算法、MyBatis Plus 自动配置 | 所有服务 |
| **distributedid** | 雪花算法 ID 生成 | CoreService |
| **designpattern** | AbstractStrategyChoose 策略模式（去重策略选择）、AbstractChainContext 责任链（创建校验链） | CoreService |
| **idempotent** | @Idempotent 注解（MQ 消费幂等、API 幂等） | 所有 MQ 消费者 |
| **convention** | Result 统一返回、PageRequest/PageResponse 分页、异常体系 | 所有服务 |
| **web** | GlobalExceptionHandler 全局异常处理、Results 工厂 | 所有服务 |
| **common** | ThreadPoolBuilder 线程池、BeanUtil 对象映射 | 所有服务 |
| **log** | @ILog 日志注解 | 关键方法 |
| **bizs/user** | UserContext 用户上下文 | project-service、stats-service |

### 2.3 服务间通信

```
                    ┌──────────────────┐
                    │  gateway-service │
                    └────────┬─────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ user-service  │  │   project    │  │    stats     │
   │  (Feign代理)  │  │   service    │  │   service    │
   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
          │                 │                  │
          │──Feign─────────►│                  │
          │──Feign────────────────────────────►│
          │                 │  ◄──RocketMQ──►  │
          │                 │                  │
          │                 │         ┌────────┴──┐
          │                 │         │   risk    │
          │                 │         │  service  │
          │                 │         └───────────┘
          │                 │  ◄──RocketMQ──►  │
          └─────────────────┴──────────────────┘
                            │
                      ┌─────┴─────┐
                      │   Redis   │
                      │   MySQL   │
                      │  RocketMQ │
                      └───────────┘
```

- **user → project**: 通过 OpenFeign 调用短链接 CRUD、回收站、URL标题（同步）
- **user → stats**: 通过 OpenFeign 调用统计查询（同步）
- **project → stats**: 通过 RocketMQ 发送 `ShortLinkStatsRecordEvent`（异步）
- **project → risk**: 通过 RocketMQ 发送 `ShortLinkRiskEvent`（异步）
- **risk → project**: 通过 RocketMQ 发送 `ShortLinkViolationEvent` 通知违规结果（异步）

## 3. 各服务详细设计

### 3.1 ShortLinkCoreService — 核心 CRUD

**职责**:
- 短链接创建（布隆过滤器/分布式锁去重）
- 短链接修改（同分组/跨分组）
- 分页查询（热库 + 冷库合并）
- 分组统计查询
- 今日统计填充

**复用 Frameworks**:
- `designpattern` → `AbstractStrategyChoose` 选择去重策略（布隆过滤器 vs 分布式锁）
- `cache` → `DistributedCache` 操作 Redis 缓存
- `database` → `BaseDO`、ShardingSphere 分片
- `convention` → `PageRequest`/`PageResponse` 分页
- `idempotent` → `@Idempotent` API 幂等

**关键方法**:
```java
public interface ShortLinkCoreService {
    // 创建
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO req);
    ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO req);

    // 修改
    void updateShortLink(ShortLinkUpdateReqDTO req);

    // 查询
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO req);
    List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> gidList);

    // 统计填充
    void fillTodayStats(IPage<ShortLinkPageRespDTO> page);
}
```

### 3.2 ShortLinkRedirectService — 跳转重定向

**职责**:
- 短链接跳转（核心高频路径）
- 多级缓存查询（Caffeine → Redis → DB）
- 缓存穿透/击穿/雪崩防护
- 冷热库查询（热库优先，冷库兜底）
- 异步发送统计事件到 MQ
- 冷数据自动回温触发

**复用 Frameworks**:
- `cache` → `MultistageCache` 多级缓存代理（核心复用点）
- `cache` → 布隆过滤器防穿透、分布式锁防击穿
- `common` → `ThreadPoolBuilder` 构建异步线程池

**关键方法**:
```java
public interface ShortLinkRedirectService {
    // 跳转
    void redirect(String shortUri, HttpServletRequest request, HttpServletResponse response);
}
```

**性能要求**: 缓存命中 < 50ms，DB 查询 < 200ms

### 3.3 ShortLinkColdDataService — 冷热数据管理

**职责**:
- 冷数据迁移（定时任务，每天 2:30 AM）
- 热数据回温（访问量 > 阈值自动迁回热库）
- 冷热数据合并查询（归并排序优化）
- 过期链接宽限期处理

**复用 Frameworks**:
- `database` → 冷库/热库双数据源操作
- `cache` → Redis 操作

**关键方法**:
```java
public interface ShortLinkColdDataService {
    // 迁移
    void migrateColdData(int batchSize);

    // 回温
    void reheatLink(String fullShortUrl);

    // 合并查询
    IPage<ShortLinkPageRespDTO> mergeColdHotPage(ShortLinkPageReqDTO req,
                                                  IPage<ShortLinkPageRespDTO> hotPage);

    // 过期处理
    void handleExpiredLinks();
}
```

### 3.4 ShortLinkCacheHelper — 缓存工具类

**职责**:
- 封装多级缓存的读写操作
- 缓存预热（创建时）
- 缓存失效（修改/删除时）
- 空值缓存处理
- Redis Pub/Sub 通知其他实例清除本地缓存

**复用 Frameworks**:
- `cache` → `MultistageCache`、`DistributedCache`、`CacheLoader`、`CacheGetFilter`

**关键方法**:
```java
public class ShortLinkCacheHelper {
    // 读取（多级缓存）
    String getOriginalUrl(String fullShortUrl);

    // 预热
    void warmUp(String fullShortUrl, String originalUrl, String gid, Date validDate);

    // 失效
    void evict(String fullShortUrl);

    // 空值标记
    void markNotExist(String fullShortUrl);
    boolean isMarkedNotExist(String fullShortUrl);
}
```

### 3.5 Stats Service — 统计服务（独立微服务）

**Maven 模块**: `services/stats-service`

**职责**:
- 消费 `ShortLinkStatsRecordEvent` 记录访问统计
- UV/UIP 去重（HyperLogLog）
- 多维度统计（浏览器、操作系统、设备、网络、地域）
- 统计查询 API
- Redis ZSet 今日排行榜维护

**复用 Frameworks**:
- `idempotent` → `@Idempotent(scene=MQ)` MQ 消费幂等
- `cache` → Redis 操作（HyperLogLog、ZSet）
- `database` → 统计表操作
- `convention` → 统一返回、分页
- `web` → 全局异常处理
- `log` → @ILog 日志

**MQ Topic**: `short-link_stats-record`

### 3.6 Risk Service — 风控服务（独立微服务）

**Maven 模块**: `services/risk-service`

**职责**:
- 消费 `ShortLinkRiskEvent` 进行 URL 风控审核
- 恶意 URL 检测（外部 API / AI 服务）
- 违规通知（发送 `ShortLinkViolationEvent`）
- 用户通知管理

**复用 Frameworks**:
- `idempotent` → MQ 消费幂等
- `cache` → Redis 操作
- `database` → 通知表操作
- `convention` → 统一返回
- `web` → 全局异常处理

**MQ Topic**: `short-link_risk-check`、`short-link_violation-notify`

## 4. Facade 模式设计

`ShortLinkService` 接口保持不变，`ShortLinkServiceImpl` 重构为纯委托：

```java
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkCoreService coreService;
    private final ShortLinkRedirectService redirectService;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO req) {
        return coreService.createShortLink(req);
    }

    @Override
    public void redirect(String shortUri, HttpServletRequest req, HttpServletResponse resp) {
        redirectService.redirect(shortUri, req, resp);
    }

    // ... 其他方法委托给对应子服务
}
```

## 5. 实施阶段

### Phase 1 — P0：Core CRUD + MQ 统一

**目标**: 新模块的 CRUD 功能完整可用

**任务清单**:
1. 重构 `ShortLinkServiceImpl` → 提取 `ShortLinkCoreService` + `ShortLinkCoreServiceImpl`
2. 提取 `ShortLinkCacheHelper` 缓存工具类
3. 将 `ShortLinkServiceImpl` 改为 Facade 委托模式
4. 实现 RocketMQ 消费者:
   - `ShortLinkCacheConsumer` — 缓存失效处理
   - `ShortLinkExpireArchiveConsumer` — 过期归档处理
5. 集成 frameworks 模块:
   - `designpattern` 策略模式用于去重策略选择
   - `idempotent` 用于 MQ 消费幂等
   - `convention` 用于统一返回和分页
6. 验证: 所有 CRUD API 正常工作

**交付物**: CoreService 完整实现 + MQ 消费者 + Facade 重构

### Phase 2 — P0：Redirect 跳转服务

**目标**: 跳转功能完整可用，性能达标

**任务清单**:
1. 实现 `ShortLinkRedirectService` + `ShortLinkRedirectServiceImpl`
2. 集成 `frameworks/cache` 的 `MultistageCache` 实现多级缓存
3. 实现缓存穿透防护（布隆过滤器 + 空值缓存）
4. 实现缓存击穿防护（分布式锁）
5. 新增 `ShortLinkRedirectController` + `GET /{shortUri}` 端点
6. 实现统计事件发送（`ShortLinkStatsRecordEvent` → RocketMQ）
7. 冷库查询兜底逻辑
8. 验证: 跳转功能正常，缓存命中 < 50ms

**交付物**: RedirectService 完整实现 + 跳转端点

### Phase 3 — P1：ColdData 冷热数据优化

**目标**: 冷热数据管理完善，合并查询性能优化

**任务清单**:
1. 实现 `ShortLinkColdDataService` + `ShortLinkColdDataServiceImpl`
2. 迁移 `ShortLinkColdMigrationJob` 定时任务
3. 实现回温机制（阈值可配置）
4. 优化冷热合并查询（归并排序替代内存排序）
5. 过期链接宽限期处理
6. CoreService 的分页查询集成 ColdDataService
7. 验证: 冷数据迁移正常，合并查询性能达标

**交付物**: ColdDataService + 迁移任务 + 合并查询优化

### Phase 4 — P1：Stats + RiskControl 独立微服务

**目标**: 统计和风控作为独立微服务运行

**任务清单**:
1. 创建 `services/stats-service` Maven 模块
   - pom.xml（依赖 frameworks 模块）
   - application.yaml（Nacos 注册、RocketMQ 配置）
   - 迁移 `ShortLinkStatsService` + `LinkAccessStatsService`
   - 实现 `ShortLinkStatsSaveConsumer`（MQ 消费者）
   - 统计查询 API + Controller
2. 创建 `services/risk-service` Maven 模块
   - pom.xml
   - application.yaml
   - 迁移 `UrlRiskControlService` + `UserNotificationService`
   - 实现 `ShortLinkRiskConsumer`（MQ 消费者）
   - 风控 API + Controller
3. Gateway 路由配置更新
4. project-service 移除统计/风控相关代码，改为 MQ 发送
5. 验证: 统计和风控独立运行，MQ 通信正常

**交付物**: stats-service + risk-service 独立微服务

### Phase 5a — P0：User-Service 补全（Feign 代理层迁移）

**目标**: user-service 完整接管旧 admin 模块的所有功能

**任务清单**:
1. 添加 OpenFeign 依赖到 user-service pom.xml
2. 创建 `ShortLinkRemoteService`（Feign → shortlink-project-service）
   - 短链接 CRUD：create、batchCreate、update、page、count
   - 回收站：save、page、recover、remove
   - URL 标题：getPageTitle
3. 创建 `ShortLinkStatsRemoteService`（Feign → shortlink-stats-service）
   - 统计查询：oneShortLinkStats、groupShortLinkStats
   - 访问记录：shortLinkStatsAccessRecord、groupShortLinkStatsAccessRecord
4. 迁移控制器（使用 framework starters，不带重复框架类）
   - `ShortLinkController` — 短链接创建/更新/分页
   - `RecycleBinController` — 回收站操作
   - `ShortLinkStatsController` — 统计查询
   - `UrlTitleController` — URL 标题提取
5. 迁移相关 DTO（remote/dto 包下的请求/响应类）
6. 添加 OpenFeign 用户上下文传递拦截器
7. 验证: 所有 Feign 代理端点正常工作

**交付物**: user-service 完整功能 + 拆分后的 Feign Client

### Phase 5b — P1：Gateway-Service 迁移

**目标**: 网关迁移至 services/ 统一管理

**任务清单**:
1. 在 services/ 下创建 gateway-service 模块
2. 迁移 gateway 代码，包名改为 `com.xhy.shortlink.biz.gatewayservice`
3. 更新路由配置中的服务名对齐新模块命名
4. 更新 services/pom.xml 注册子模块
5. 验证: 网关路由正常，Token 校验正常

**交付物**: services/gateway-service 模块

### Phase 5c — 路由配置对齐

**目标**: 确保网关路由指向正确的服务名

**路由映射**:
| 路径 | 目标服务 |
|------|---------|
| `/api/short-link/admin/**` | `shortlink-user-service` |
| `/api/short-link/v1/stats/**` | `shortlink-stats-service` |
| `/api/short-link/**` | `shortlink-project-service` |

### Phase 6 — P2：测试、文档、Aggregation 适配

**目标**: 质量保障和部署完善

**任务清单**:
1. 单元测试（目标 80% 覆盖率）
   - CoreService 测试
   - RedirectService 测试
   - ColdDataService 测试
   - MQ 消费者测试
2. 集成测试
   - API 端到端测试
   - MQ 消息流转测试
3. Aggregation 聚合模块适配
   - 更新 aggregation 模块依赖
   - 确保聚合部署模式可用
4. 文档完善
   - 架构设计文档
   - API 文档
   - 部署文档

**交付物**: 测试套件 + Aggregation 适配 + 文档

## 6. 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| Service 拆分后方法间调用增多，性能下降 | 中 | 子服务在同一 JVM 内，方法调用开销可忽略；CacheHelper 避免远程调用 |
| MultistageCache 框架与现有缓存逻辑不完全匹配 | 中 | Phase 2 中评估，必要时扩展 MultistageCache 或保留自定义实现 |
| Stats/Risk 独立后，跨服务数据一致性 | 中 | 使用 RocketMQ 事务消息 + 幂等消费保证最终一致性 |
| 冷热合并查询归并排序实现复杂度 | 低 | 分步实现，先保证功能正确，再优化性能 |
| Aggregation 适配工作量不确定 | 低 | 放在最后阶段，不影响核心重构 |
| Feign Client 拆分后服务名不匹配 | 中 | Phase 5c 统一对齐路由配置 |
| Gateway 迁移后 Sa-Token 配置丢失 | 低 | 完整迁移配置文件，逐项验证 |

## 7. 目录结构（Phase 1 完成后）

```
services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/
├── common/
│   ├── constant/
│   │   ├── RedisKeyConstant.java
│   │   ├── RocketMQConstant.java
│   │   └── ShortLinkConstant.java
│   └── enums/
│       ├── LinkEnableStatusEnum.java
│       ├── ValidDateTypeEnum.java
│       └── OrderTagEnum.java
├── config/
│   ├── RBloomFilterConfiguration.java
│   ├── RedisConfiguration.java
│   └── ShortLinkLocalCacheConfig.java
├── controller/
│   └── ShortLinkController.java
├── dao/
│   ├── entity/
│   │   ├── ShortLinkDO.java
│   │   ├── ShortLinkGoToDO.java
│   │   ├── ShortLinkColdDO.java
│   │   └── ShortLinkGoToColdDO.java
│   └── mapper/
│       ├── ShortLinkMapper.java
│       ├── ShortLinkGoToMapper.java
│       ├── ShortLinkColdMapper.java
│       └── ShortLinkGoToColdMapper.java
├── dto/
│   ├── req/
│   │   ├── ShortLinkCreateReqDTO.java
│   │   ├── ShortLinkBatchCreateReqDTO.java
│   │   ├── ShortLinkUpdateReqDTO.java
│   │   └── ShortLinkPageReqDTO.java
│   └── resp/
│       ├── ShortLinkCreateRespDTO.java
│       ├── ShortLinkBatchCreateRespDTO.java
│       ├── ShortLinkBaseInfoRespDTO.java
│       ├── ShortLinkPageRespDTO.java
│       └── ShortLinkGroupCountRespDTO.java
├── helper/
│   └── ShortLinkCacheHelper.java          ← 新增
├── mq/
│   ├── event/
│   │   ├── ShortLinkRiskEvent.java
│   │   ├── ShortLinkExpireArchiveEvent.java
│   │   └── UpdateFaviconEvent.java
│   ├── producer/
│   │   ├── AbstractCommonSendProduceTemplate.java
│   │   ├── ShortLinkCacheProducer.java
│   │   ├── ShortLinkRiskProducer.java
│   │   └── ShortLinkExpireArchiveProducer.java
│   └── consumer/                          ← 新增
│       ├── ShortLinkCacheConsumer.java
│       └── ShortLinkExpireArchiveConsumer.java
├── service/
│   ├── ShortLinkService.java              # Facade 接口（不变）
│   ├── ShortLinkServiceImpl.java          # Facade 实现（委托）← 重构
│   ├── ShortLinkCoreService.java          ← 新增
│   ├── impl/
│   │   └── ShortLinkCoreServiceImpl.java  ← 新增
│   └── strategy/
│       ├── BloomFilterDeduplicationStrategy.java
│       └── DistributedLockDeduplicationStrategy.java
└── toolkit/
    ├── LinkUtil.java
    └── HashUtil.java
```

## 8. 进度追踪

| 阶段 | 状态 | 提交记录 | 日期 |
|------|------|---------|------|
| Phase 1 — Core CRUD + MQ 统一 | ✅ 已完成 | `910aea6`~`4858131` | 2026-02-16 ~ 2026-02-18 |
| Phase 2 — Redirect 跳转服务 | ✅ 已完成 | `cfb5772`~`2a45161` | 2026-02-18 ~ 2026-02-19 |
| Phase 3 — ColdData 冷热数据优化 | ✅ 已完成 | `65c2b2e`~`141d8c5` | 2026-02-19 ~ 2026-02-20 |
| Phase 4a — Stats Service | ✅ 已完成 | `b060e43`~`4bc437a` | 2026-02-20 |
| Phase 4b — Risk Service | ✅ 已完成 | `355edde` | 2026-02-20 |
| Phase 5a — User-Service 补全 | ⬜ 未开始 | — | — |
| Phase 5b — Gateway-Service 迁移 | ⬜ 未开始 | — | — |
| Phase 5c — 路由配置对齐 | ⬜ 未开始 | — | — |
| Phase 6 — 测试、文档、Aggregation | ⬜ 未开始 | — | — |
