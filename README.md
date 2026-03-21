# ShortLink

> 一个面向 **SaaS 场景** 的短链接平台，包含短链创建、跳转、统计分析、风控封禁、站内通知以及完整的监控与验收闭环。

[![Java](https://img.shields.io/badge/Java-17-blue)](#环境要求)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen)](#技术栈)
[![Vue](https://img.shields.io/badge/Vue-3.x-42b883)](#技术栈)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange)](./format/copyright.txt)

---

## 项目简介

`ShortLink` 是一个围绕“**短链接全生命周期管理**”构建的多模块系统，除了最基础的短链创建与跳转，还覆盖了：

- 用户与分组管理
- PV / UV / UIP 统计
- 风控检测与自动封禁
- 封禁后的站内通知与实时提醒
- 本地与监控环境的一体化联调
- Prometheus / Grafana / Alertmanager 可观测性闭环

它既可以作为一个实际可运行的短链接平台，也很适合作为：

- Spring Boot / Spring Cloud 微服务拆分示例
- ShardingSphere 分库分表实践样例
- RocketMQ 异步事件驱动样例
- Redis / Redisson / BloomFilter / 本地缓存综合实践样例
- 可观测性与发布验收流程示例

---

## 核心特性

### 短链接能力
- 单个 / 批量创建短链接
- 短链跳转与 302 重定向
- 回收站、恢复与彻底删除
- 冷热数据迁移与冷数据回温

### 用户后台能力
- 用户注册、登录、退出
- 分组创建、排序、编辑、删除
- 用户创建的短链封禁后，顶部铃铛实时通知

### 数据与统计能力
- PV / UV / UIP 多维度统计
- 访问日志、设备、浏览器、操作系统、网络、地域统计
- 分组维度与单链维度统计查询

### 风控能力
- 高风险链接识别
- 自动封禁违规短链接
- 生成站内通知并通过 WebSocket 推送给在线用户

### 工程与运维能力
- 微服务拆分：`gateway / user / project / stats / risk / aggregation`
- Nacos 服务注册发现
- RocketMQ 异步事件解耦
- Prometheus / Grafana / Alertmanager 监控闭环
- Phase 7 联调与验收已完成，当前结论为 **Go**

---

## 系统架构

```text
                        +----------------------+
                        |    console-vue UI    |
                        |  Vue3 + ElementPlus  |
                        +----------+-----------+
                                   |
                                   v
                        +----------------------+
                        |   gateway-service    |
                        | Spring Cloud Gateway |
                        +----+-------------+---+
                             |             |
         +-------------------+             +-------------------+
         |                                                       |
         v                                                       v
+----------------------+                           +----------------------+
|    user-service      |                           |   project-service    |
| 用户/分组/通知/WebSocket |                           | 短链创建/跳转/回收站 |
+----------+-----------+                           +----------+-----------+
           |                                                      |
           | Feign / MQ                                            | MQ
           v                                                      v
+----------------------+                           +----------------------+
|    stats-service     |                           |    risk-service      |
| 统计消费与聚合分析     |                           | 风控检测 / 自动封禁   |
+----------------------+                           +----------+-----------+
                                                             |
                                                             | MQ
                                                             v
                                                   +----------------------+
                                                   | 用户通知创建事件推送 |
                                                   +----------------------+

基础设施：
- MySQL / ShardingSphere
- Redis / Redisson / BloomFilter / Caffeine
- RocketMQ
- Nacos
- Prometheus / Grafana / Alertmanager
```

---

## 项目结构

```text
.
├── services/             # 当前主线微服务模块
│   ├── shortlink-api
│   ├── user-service
│   ├── project-service
│   ├── stats-service
│   ├── risk-service
│   ├── gateway-service
│   └── aggregation-service
├── frameworks/           # 可复用基础能力 starter
│   ├── base
│   ├── common
│   ├── web
│   ├── database
│   ├── cache
│   ├── distributedid
│   ├── idempotent
│   └── ...
├── dependencies/         # 统一 BOM 与依赖版本管理
├── console-vue/          # 控制台前端（Vue 3）
├── docker/               # 本地依赖与监控栈编排
├── docs/                 # 设计、实施计划、验收记录、回滚文档
└── README.md
```

> 说明：当前主线后端代码统一位于 `services/` 目录。

---

## 技术栈

### 后端
- Java 17
- Spring Boot 3.2.x
- Spring Cloud / Spring Cloud Alibaba
- MyBatis-Plus
- ShardingSphere
- RocketMQ
- Redis / Redisson / Caffeine
- Sa-Token
- Micrometer + Actuator

### 前端
- Vue 3
- Vue Router
- Element Plus
- Axios
- ECharts

### 运维与观测
- Docker Compose
- Nacos
- Prometheus
- Grafana
- Alertmanager

---

## 环境要求

在本地运行前，建议准备以下环境：

- JDK 17
- Maven 3.9+
- Node.js 18+
- npm 或 pnpm
- Docker Desktop / Docker Engine
- MySQL 客户端（可选，便于排查）

---

## 快速开始

### 1）启动基础依赖与监控栈

项目已提供一套本地依赖编排：

```bash
docker compose -f docker/docker-compose.yml up -d mysql redis nacos namesrv broker dashboard alertmanager prometheus grafana
```

默认会拉起：
- MySQL
- Redis
- Nacos
- RocketMQ NameServer / Broker / Dashboard
- Prometheus / Grafana / Alertmanager

### 2）启动后端服务

你可以分别启动服务，也可以只启动需要联调的几个。

常用服务：
- `gateway-service`：`8000`
- `project-service`：`8001`
- `user-service`：`8002`
- `aggregation-service`：`8003`
- `stats-service`：`8004`
- `risk-service`：`8005`

示例：

```bash
mvn -pl services/gateway-service -am spring-boot:run
mvn -pl services/project-service -am spring-boot:run
mvn -pl services/user-service -am spring-boot:run
mvn -pl services/stats-service -am spring-boot:run
mvn -pl services/risk-service -am spring-boot:run
```

> 如果某个默认端口已被占用，可以临时覆盖，例如：
>
> ```bash
> mvn -pl services/project-service -am spring-boot:run -Dspring-boot.run.arguments="--server.port=8011"
> ```

### 3）启动前端控制台

```bash
cd console-vue
npm install
npm run dev
```

开发环境默认通过 Vite 代理把 `/api` 请求转到：
- `http://127.0.0.1:8000`

### 4）访问入口

- 控制台前端：通常是 Vite 默认开发地址
- RocketMQ Dashboard：`http://127.0.0.1:8080`
- Prometheus：`http://127.0.0.1:9090`
- Grafana：`http://127.0.0.1:3000`
- Alertmanager：`http://127.0.0.1:9093`
- Nacos：`http://127.0.0.1:8848/nacos`

---

## 自动部署规划

当前仓库已经形成一版基于现状模块结构的 CI/CD 自动部署实施方案，目标是：

- 推送 `main` 后自动构建并推送镜像到 GHCR
- 在云服务器上自动拉起完整业务拓扑
- 保留当前生产所需的 `gateway-service + aggregation-service + stats-service + risk-service + console-vue`

实施计划文档见：

- [docs/plans/2026-03-21-cicd-automated-deployment-implementation-plan.md](./docs/plans/2026-03-21-cicd-automated-deployment-implementation-plan.md)

当前已经补齐的落地文件包括：

- GitHub Actions 工作流：`.github/workflows/deploy.yml`
- 应用层编排：`docker/docker-compose.app.yml`
- 镜像构建文件：`docker/Dockerfile.backend`、`docker/Dockerfile.frontend`
- 前端 Nginx 配置：`docker/nginx/default.conf`
- 服务器初始化脚本：`deploy/setup-server.sh`
- 应用运行环境模板：`docker/.env.example`

正式启用前，需要在 GitHub 仓库 Secrets 中配置至少以下变量：

- `SERVER_HOST`
- `SERVER_USER`
- `SERVER_SSH_KEY`
- `GHCR_USERNAME`
- `GHCR_TOKEN`
- `DASHSCOPE_API_KEY`
- `SHORT_LINK_DOMAIN_DEFAULT`
- `SHORT_LINK_STATS_LOCALE_AMAP_KEY`

如果生产环境开启 Nacos 鉴权，再补充：

- `NACOS_USERNAME`
- `NACOS_PASSWORD`

这份计划明确了：

- 基于 `services/*` 的正确 Maven 构建目标
- `gateway-service` 的 `aggregation` 部署 profile
- GHCR 镜像命名与 GitHub Actions 发布流程
- `docker-compose.yml + docker-compose.app.yml` 的部署方式
- 服务端初始化、上线校验与回滚要求

---

## 本地开发建议

### Maven 构建

```bash
mvn clean package
```

只构建指定服务：

```bash
mvn -pl services/user-service -am package
mvn -pl services/project-service -am package
```

### 前端构建

```bash
cd console-vue
npm run build
```

### 代码规范

项目使用：
- Checkstyle
- Spotless

建议在提交前执行：

```bash
mvn validate
```

更多规范可参考：
- `CODE_STYLE.md`
- `checkstyle/`
- `format/`

---

## 通知能力说明

当前系统已经补齐“**短链被封禁后通知用户**”的体验链路：

- `risk-service` 识别违规短链并封禁
- 生成站内通知记录
- 通知事件推送到 `user-service`
- `user-service` 通过 WebSocket 推送给在线用户
- 前端控制台顶部显示铃铛与未读角标
- 点击铃铛后以**下拉通知面板**展示通知，无需跳转页面

这条链路已完成实现与联调验证。

---

## 监控与验收

项目已经落地：
- Prometheus 抓取
- Grafana 仪表盘
- Alertmanager 告警
- 最小业务链路验收

当前最新验收记录：
- `docs/refactor/phase7-acceptance-report-2026-03-06.md`

关键结论：
- **Phase 7 联调与验收闭环已完成，当前结论为 Go**

相关文档：
- `docs/refactor/phase7-release-checklist.md`
- `docs/refactor/phase7-rollback-playbook.md`
- `docs/refactor/phase7-acceptance-report-template.md`

---

## 重构进度

根据当前主线状态，核心重构已经完成，并进入“发布/稳定化”阶段。

已完成主阶段：
- Phase 1：Core CRUD + MQ 统一
- Phase 2：Redirect 跳转服务
- Phase 3：冷热数据优化
- Phase 4：Stats / Risk 服务拆分
- Phase 5：User / Gateway / 路由对齐
- Phase 7：监控、联调、验收闭环

参考文档：
- `docs/plans/2026-02-16-project-service-refactor-design.md`
- `docs/refactor/phase7-acceptance-report-2026-03-06.md`

---

## 文档索引

### 设计与实施
- `docs/plans/2026-02-16-project-service-refactor-design.md`
- `docs/plans/2026-03-02-phase7-release-acceptance-design.md`
- `docs/plans/2026-03-07-notification-bell-design.md`
- `docs/plans/2026-03-07-notification-bell-implementation-plan.md`

### 验收与回滚
- `docs/refactor/phase7-acceptance-report-2026-03-02.md`
- `docs/refactor/phase7-acceptance-report-2026-03-06.md`
- `docs/refactor/phase7-release-checklist.md`
- `docs/refactor/phase7-rollback-playbook.md`

### 需求与历史资料
- `docs/refactor/requirement.md`

---

## 提交规范

建议使用 Conventional Commits 风格：

```text
feat(scope): description
fix(scope): description
refactor(scope): description
docs(scope): description
test(scope): description
chore(scope): description
```

项目内已有大量这类提交记录，可直接保持一致。

---

## 压测文件

仓库根目录保留了两个 JMeter 脚本，可作为性能测试起点：

- `创建短链接压测.jmx`
- `跳转短链接压测.jmx`

---

## Roadmap

后续可以继续演进的方向包括：

- 更丰富的通知中心能力（分类、删除、归档）
- 更完整的风控策略与可解释原因
- 更完善的前端代码拆分与构建优化
- 更系统的自动化验收脚本与 CI/CD 集成

---

## License

本项目遵循 Apache 2.0 风格版权头规范，具体以仓库内版权与格式配置为准。
