# Phase 7 验收记录（2026-03-06）

## 1. 基本信息

- 验收日期：2026-03-06
- 验收人：Codex
- 环境：Windows 宿主启动业务服务，Docker Desktop 运行依赖与监控栈，WSL 辅助排障
- 版本状态：本地 `main` 工作区（包含本次联调修复）

## 2. 本次联调修复项

- `docker/docker-compose.yml`
  - 为 `nacos` 补充 `9848/9849` 端口映射，恢复 Nacos 2.x gRPC 发现链路。
- `services/user-service/pom.xml`
  - 补充 `spring-boot-maven-plugin`，产出可执行 Boot Jar。
- `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/remote/ShortLinkRemoteService.java`
  - 修正 `listGroupShortLinkCount` 的请求参数名，从 `requestParam` 改为 `gidList`。
- `services/stats-service/src/main/resources/application.yaml`
  - 切换为 ShardingSphere Driver，恢复逻辑表 `t_link` 的分片访问。
- `services/stats-service/src/main/resources/shardingsphere-config-dev.yaml`
  - 新增 stats-service 的分片配置。
- `services/risk-service/src/main/resources/application.yaml`
  - 切换为 ShardingSphere Driver，恢复逻辑表 `t_link` 的分片访问。
- `services/risk-service/src/main/resources/shardingsphere-config-dev.yaml`
  - 新增 risk-service 的分片配置。

## 3. 监控与预检结果

### 3.1 服务端点

以下 6 个服务均返回：
- `/actuator/health` -> `200`
- `/actuator/prometheus` -> `200`

服务端口：
- `8000` gateway-service
- `8001` project-service
- `8002` user-service
- `8003` aggregation-service
- `8004` stats-service
- `8005` risk-service

### 3.2 Prometheus / Grafana / Alertmanager

- Prometheus `shortlink-services` 6 个 scrape target 全部 `up`
- Grafana 查询到仪表盘：`ShortLink Overview`
- Alertmanager 手工测试告警 `ManualTestAlert` 已进入 `active`

### 3.3 说明

- 由于本次业务服务运行在 Windows 宿主，而 WSL 中无法直接通过 `127.0.0.1` 访问这些端口，未直接运行 `docker/monitoring/scripts/phase7-preflight.sh` 的 bash 全量模式。
- 但已在 Windows 侧逐项执行与该脚本等价的全量检查：6 个服务 `health/prometheus`、Prometheus target、Grafana dashboard、Alertmanager 告警。

## 4. 最小业务冒烟结果

本次通过直连业务服务完成一轮最小冒烟：

1. 用户注册成功
2. 用户登录成功
3. 创建分组成功
4. 创建短链接成功
5. 访问短链接返回 `302`，跳转到 `https://example.com`

### 4.1 最终样例

- 用户名：`phase7_1772811261`
- 分组 gid：`2029943725098274816`
- 短链接：`http://nurl.ink:8001/awSen`
- 跳转结果：`302 -> https://example.com`

### 4.2 最终指标增量

- project-service
  - `shortlink_create_success_total`：`+1`
  - `shortlink_redirect_success_total`：`+1`
- stats-service
  - `shortlink_mq_consume_success_total`：`+1`
  - `shortlink_mq_consume_failure_total`：`+0`
- risk-service
  - `shortlink_mq_consume_success_total`：`+2`
  - `shortlink_mq_consume_failure_total`：`+0`

## 5. 当前结论

### 5.1 已完成

- 6 个服务可用并暴露监控端点
- Prometheus / Grafana / Alertmanager 链路可用
- 创建 / 跳转 / 统计消费链路验证通过
- 风控 MQ 消费链路已在真实 `DASHSCOPE_API_KEY` 下验证通过
- user-service 分组统计远调参数问题已修复
- stats-service 分表更新问题已修复
- risk-service 分表访问问题已修复

### 5.2 最终结论

- 本轮 Phase 7 联调与验收闭环已完成，当前结论为 **Go**。
- 需要说明的是：由于业务服务运行在 Windows 宿主、WSL 无法直接访问这些宿主端口，本次保留的是与 `phase7-preflight.sh` 等价的逐项校验结果，而不是原 bash 脚本在 WSL 中的直接回执。

## 6. 建议下一步

1. 将本次联调修复与最终验收记录提交入库
2. 如需留存更强形式的脚本证据，可在可直连 Windows 宿主端口的 shell 环境中补跑 `docker/monitoring/scripts/phase7-preflight.sh`
3. 进入发布或推送阶段
