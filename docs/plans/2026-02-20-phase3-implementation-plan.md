# Phase 3 — ColdData 冷热数据优化 实施计划

## 目标

完善冷热数据生命周期管理：定时迁移 → 回温机制 → 过期归档（ARCHIVE 阶段）。

## 现状分析

### 已完成（Phase 1/2）
- `ShortLinkColdDO` / `ShortLinkGoToColdDO` 实体 ✅
- `ShortLinkColdMapper` / `ShortLinkGoToColdMapper` ✅
- `ShortLinkExpireArchiveConsumer` FREEZE 阶段 ✅
- `ShortLinkRedirectServiceImpl.loadFromDb()` 冷表兜底查询 ✅
- `ShortLinkCoreServiceImpl` 冷热合并分页查询 ✅
- `ShortLinkCoreServiceImpl.updateShortLink()` 冷数据更新处理 ✅

### 待实现（Phase 3）
1. History 实体 + Mapper（归档目标表）
2. `ShortLinkMapper.deletePhysical()` 物理删除方法
3. `ShortLinkExpireArchiveConsumer` ARCHIVE 阶段（冻结期结束后迁入历史库）
4. `ShortLinkColdMigrationJob` 定时迁移任务（热→冷）
5. `rehotColdLink()` 回温机制（冷→热）
6. `ColdDataProperties` 配置类（外部化 @Value 注解）

## 实施步骤

### Step 1：新增 History 实体和 Mapper

创建归档目标表对应的实体和 Mapper：

**新建文件：**
- `dao/entity/ShortLinkHistoryDO.java` — 历史短链接实体（`t_link_history`）
- `dao/entity/ShortLinkGoToHistoryDO.java` — 历史路由实体（`t_link_goto_history`）
- `dao/mapper/ShortLinkHistoryMapper.java` — 历史短链接 Mapper
- `dao/mapper/ShortLinkGoToHistoryMapper.java` — 历史路由 Mapper

**参考：** 旧模块 `project/src/main/java/.../dao/entity/ShortLinkHistoryDO.java`

### Step 2：ColdDataProperties 配置类

提取 `@Value` 注解为 `@ConfigurationProperties` 类：

```yaml
short-link:
  cold-data:
    enabled: true
    days: 90
    batch-size: 200
    cron: "0 30 2 * * ?"
    rehot:
      threshold: 1000
```

**新建文件：** `config/ColdDataProperties.java`

同时更新 `ShortLinkExpireArchiveConsumer` 中的 `graceDays` 也纳入配置类管理。

### Step 3：实现 ARCHIVE 阶段（ShortLinkExpireArchiveConsumer）

替换当前的 `log.info` 占位逻辑，实现完整归档流程：

1. 查询热表/冷表中状态为 FROZEN 的链接
2. 复制到 `t_link_history` / `t_link_goto_history`
3. 物理删除源表记录
4. 清除缓存

**修改文件：** `mq/consumer/ShortLinkExpireArchiveConsumer.java`

**参考：** 旧模块 `ShortLinkExpireArchiveRocketMQConsumer.archiveFromHot()` / `archiveFromCold()`

### Step 4：实现 ShortLinkColdMigrationJob 定时迁移

每日凌晨 2:30 扫描热表，将超过 N 天未访问的链接迁移到冷表：

1. 按 `last_access_time < threshold` 分批查询热表
2. 逐条复制到冷表（去重检查）
3. 物理删除热表记录
4. 清除缓存（Redis + 本地 + MQ 广播）

**新建文件：** `job/ShortLinkColdMigrationJob.java`

**参考：** 旧模块 `project/.../job/ShortLinkColdMigrationJob.java`

### Step 6：实现 rehotColdLink 回温机制

当冷链接被频繁访问（超过阈值）时，从冷表迁回热表：

1. 查询冷表记录 + 冷路由表记录
2. 插入热表（去重检查）
3. 删除冷表记录
4. 清除缓存

**修改文件：** `ShortLinkRedirectServiceImpl.java` — 在跳转命中冷表时触发回温判断

**触发条件：** 冷链接被访问时，通过 Redis INCR 计数，达到 `rehot.threshold` 时异步执行回温。

### Step 6：编译验证 + 代码注释

- 确认所有新增文件编译无误
- 为关键方法添加 Javadoc 和逻辑注释

## 文件变更清单

| 操作 | 文件 |
|------|------|
| 新建 | `dao/entity/ShortLinkHistoryDO.java` |
| 新建 | `dao/entity/ShortLinkGoToHistoryDO.java` |
| 新建 | `dao/mapper/ShortLinkHistoryMapper.java` |
| 新建 | `dao/mapper/ShortLinkGoToHistoryMapper.java` |
| 新建 | `config/ColdDataProperties.java` |
| 新建 | `job/ShortLinkColdMigrationJob.java` |
| 修改 | `mq/consumer/ShortLinkExpireArchiveConsumer.java` — ARCHIVE 阶段 |
| 修改 | `service/impl/ShortLinkRedirectServiceImpl.java` — 回温触发 |
