# Phase 7 发布与验收设计文档（Prometheus + Grafana + Alertmanager）

## 1. 背景与目标

当前重构已进入 Phase 6，核心服务拆分与 aggregation 适配已经完成，缺口集中在“可观测性落地 + 发布验收流程标准化”。

Phase 7 目标：

1. 为 `project/user/stats/risk/gateway/aggregation` 六个服务统一接入指标暴露能力。
2. 落地监控栈（Prometheus + Grafana + Alertmanager）并形成最小可用告警闭环。
3. 固化发布检查、灰度验证、回滚演练与最终验收标准，确保可重复执行。

## 2. 方案选型

采用“分层渐进切换”：

1. 应用层先接入 `actuator + prometheus` 端点，验证每个服务指标可拉取。
2. 平台层接入 Prometheus 抓取与 Grafana 仪表盘，先观测后告警。
3. 最后开启告警规则与发布验收流程，进入稳定运行。

选择原因：

- 风险可控：每层都可独立回退。
- 问题易定位：可区分“应用指标问题”与“监控平台问题”。
- 不阻塞主业务：监控栈异常不应导致业务不可用。

## 3. 架构设计

### 3.1 应用层

每个业务服务统一接入：

- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`

统一配置：

- 开放 `health/info/prometheus` 端点；
- 启用 HTTP 请求耗时 histogram 与 percentiles；
- 保持默认安全边界，不额外暴露敏感端点。

### 3.2 平台层

新增 `docker/monitoring/`：

- `prometheus.yml`：抓取 6 个服务指标端点；
- `alert.rules.yml`：服务可用性、错误率、延迟、MQ 消费异常等规则；
- `alertmanager.yml`：默认路由（本地日志/webhook 起步）；
- `grafana/provisioning/`：数据源和仪表盘自动加载；
- `grafana/dashboards/`：服务总览与链路关键指标看板。

`docker/docker-compose.yml` 增加：

- `prometheus`
- `grafana`
- `alertmanager`

并与现有网络互通，保证在本地或测试环境可一键拉起。

### 3.3 指标模型（最小必要）

优先落地以下业务指标：

1. 创建短链成功/失败计数；
2. 跳转请求总量、失败量、耗时分布（P95）；
3. 缓存命中/未命中计数；
4. RocketMQ 消费成功/失败与处理耗时；
5. 冷数据迁移批次与处理条数。

## 4. 发布与验收流程

### 4.1 发布前检查（Preflight）

1. 六服务均可访问 `/actuator/health` 与 `/actuator/prometheus`。
2. Prometheus Targets 全部 `UP`。
3. Grafana 看板可见核心指标。
4. 告警测试规则可触发并被 Alertmanager 接收。
5. 核心功能链路冒烟通过（创建、跳转、统计、风控）。

### 4.2 灰度验证

发布顺序：

1. 监控栈；
2. 业务服务指标改造版本。

观察窗口建议 30-60 分钟，重点观察：

- HTTP 5xx 比例；
- 跳转链路 P95；
- MQ 消费失败率和延迟；
- 缓存命中率异常波动。

### 4.3 正式发布

推荐分批：

1. `gateway`
2. `user/project`
3. `stats/risk`
4. `aggregation`

每批后执行健康检查与核心接口冒烟，异常即停。

### 4.4 回滚策略

1. 监控栈与业务解耦，可单独停用监控组件。
2. 应用侧指标配置通过 `management` 配置快速回退。
3. 告警规则文件版本化，可回滚到上一版本规则集。

## 5. 风险与应对

1. 指标基数过高导致 Prometheus 压力上升  
应对：限制高基数标签，优先保留必要维度。

2. 告警噪声过大  
应对：先以高优先级规则上线，观察后逐步加细粒度规则。

3. 发布中指标缺失影响验收  
应对：将“端点可访问 + targets 全 UP”设为硬门禁，未通过不进入下一步。

## 6. 完成定义（DoD）

1. 六个服务指标端点统一可用。
2. Prometheus/Grafana/Alertmanager 在项目内可复现启动。
3. 至少一套总览仪表盘 + 一组核心告警规则已生效。
4. 发布清单、回滚清单、验收清单文档齐备。
5. 至少一次回滚演练记录完成并留痕。

