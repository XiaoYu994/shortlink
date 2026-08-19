# Phase 4a: Stats Service 独立微服务

## Context
将统计功能从 project-service 拆分为独立微服务 `stats-service`（Port 8004），通过 RocketMQ 消费统计事件，独立提供统计查询 API。

## 实施步骤

### Step 1: Maven 模块骨架
- 创建 `services/stats-service/` 目录结构
- 创建 `pom.xml`（依赖 frameworks 模块 + RocketMQ + Caffeine）
- 创建启动类 `StatsServiceApplication.java`
- 创建 `application.yaml`（端口 8004、数据源、Redis、RocketMQ、Nacos）
- 更新 `services/pom.xml` 添加 `stats-service` 子模块

### Step 2: 实体 + Mapper 迁移
从旧模块复制并适配包名：
- 7 个实体 DO：LinkAccessStatsDO、LinkAccessLogsDO、LinkBrowserStatsDO、LinkDeviceStatsDO、LinkLocaleStatsDO、LinkNetworkStatsDO、LinkOsStatsDO
- 7 个 Mapper 接口（含自定义方法）
- Mapper XML（自定义 SQL）
- 辅助实体：ShortLinkDO（仅用于 incrementStats）、ShortLinkColdDO、ShortLinkGoToDO
- 辅助 Mapper：ShortLinkMapper（仅 incrementStats）、ShortLinkColdMapper（仅 incrementStats）、ShortLinkGoToMapper

### Step 3: DTO 迁移
- 4 个请求 DTO：ShortLinkStatsReqDTO、ShortLinkStatsGroupReqDTO、ShortLinkStatsAccessRecordReqDTO、ShortLinkStatsAccessRecordGroupReqDTO、ShortLinkUvTypeReqDTO
- 11 个响应 DTO：ShortLinkStatsRespDTO 及其子 DTO
- 事件 DTO：ShortLinkStatsRecordEvent
- 常量类：RedisKeyConstant、RocketMQConstant、ShortLinkConstant、OrderTagEnum

### Step 4: MQ 消费者
- `ShortLinkStatsSaveConsumer`：消费 `ShortLinkStatsRecordEvent`，写入统计数据
- 使用 `@Idempotent` 注解替代旧的 `MessageQueueIdempotentHandler`
- 移除旧模块中的 `rehotColdLink` 调用（回温已在 RedirectService 中实现）

### Step 5: 统计查询服务
- `ShortLinkStatsService` 接口 + `ShortLinkStatsServiceImpl` 实现
- `ShortLinkStatsController`（4 个 GET 端点）
- 需要 GroupDO + LinkGroupMapper 用于权限校验

### Step 6: Gateway 路由 + 验证
- 更新 `gateway/application-dev.yaml` 添加 stats-service 路由
- 编译验证

## 关键源文件（旧模块）
- `project/src/main/java/.../service/Impl/ShortLinkStatsServiceImpl.java`
- `project/src/main/java/.../mq/consumer/RocketMQ/ShortLinkStatsSaveRocketMQConsumer.java`
- `project/src/main/java/.../controller/ShortLinkStatsController.java`
- `project/src/main/java/.../dao/entity/Link*StatsDO.java`
- `project/src/main/java/.../dao/mapper/Link*StatsMapper.java`
