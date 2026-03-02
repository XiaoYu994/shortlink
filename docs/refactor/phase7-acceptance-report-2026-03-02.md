# Phase 7 验收记录（2026-03-02）

## 1. 基本信息

- 验收日期：2026-03-02
- 验收人：Codex
- 环境：本地开发环境
- 版本状态：`main` 分支（包含 Phase 7 监控与指标改造提交）

## 2. 已完成项

- 六个服务已接入 `actuator + micrometer-registry-prometheus`。
- 六个服务 `application.yaml` 已加入统一 `management` 指标配置。
- 监控栈配置已落地：Prometheus / Grafana / Alertmanager。
- 核心业务指标埋点已接入：
  - project-service：创建成功/失败、跳转成功/失败、跳转耗时、冷迁移批次
  - stats-service：MQ 消费成功/失败、消费耗时
  - risk-service：MQ 消费成功/失败、消费耗时

## 3. 本次验证结果

### 3.1 已通过

- `docker compose -f docker/docker-compose.yml config`：通过。
- `mvn -pl services/project-service,services/stats-service,services/risk-service -am -Dtest=ShortLinkMetricsTest,StatsMetricsTest,RiskMetricsTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- 目标测试通过：
  - `ShortLinkMetricsTest`：3/3 通过
  - `StatsMetricsTest`：3/3 通过
  - `RiskMetricsTest`：3/3 通过
- `docker/monitoring/scripts/phase7-preflight.sh --skip-endpoint-check`：通过（静态门禁）。
- 监控容器已拉起：`alertmanager/prometheus/grafana` 均为 `Up`。
- 监控端口就绪检查：
  - `http://127.0.0.1:9090/-/ready` -> `200`
  - `http://127.0.0.1:3000/login` -> `200`
  - `http://127.0.0.1:9093/-/ready` -> `200`

### 3.2 环境问题处理

- 现象：环境默认代理指向 `127.0.0.1:7897`，且沙箱内 DNS 解析 `repo.maven.apache.org` 失败。
- 处理：切换到提权网络执行 Maven，补齐缺失依赖（含 `micrometer-registry-prometheus:1.12.5`）。
- 结果：构建链路恢复，目标服务模块可正常编译并执行测试。
- 预检脚本修复：本地端点检查已显式禁用代理，避免 `curl` 被代理劫持导致误报。

### 3.3 待完成项

- `docker/monitoring/scripts/phase7-preflight.sh` 全量模式目前失败，原因为业务服务未启动（端口 `8000~8005` 不可达），不再是代理误报。

## 4. 下一步

1. 启动业务服务和监控栈，执行 `docker/monitoring/scripts/phase7-preflight.sh` 全量验收。
2. 补齐 Prometheus targets、Grafana 看板截图与告警演练结果，形成最终 Go/No-Go 结论。
