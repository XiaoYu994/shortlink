# ShortLink

ShortLink 是一个面向 SaaS 场景的短链接平台。它提供短链的创建、跳转、统计、回收、冷热数据管理和风险控制，并配套 Vue 控制台、微服务拆分、消息驱动和可观测性配置。

项目的核心目标不是只生成一个短 URL，而是覆盖短链从创建到失效、统计、封禁和恢复的完整生命周期。

## 项目状态

主线功能已经覆盖以下链路：

- 用户注册、登录、退出和分组管理
- 单条和批量短链接创建
- 短链跳转（HTTP 302）
- 短链编辑、回收、恢复和彻底删除
- PV / UV / UIP 统计以及访问明细
- 热数据与冷数据迁移、查询和回温
- 风险识别、自动封禁和站内通知
- WebSocket 通知推送
- Prometheus、Grafana、Alertmanager 监控配置
- Docker 镜像构建和服务器部署脚本

当前仓库已经进入发布与稳定化阶段。主线上的历史联调记录见 [`docs/refactor/phase7-acceptance-report-2026-03-06.md`](docs/refactor/phase7-acceptance-report-2026-03-06.md)，实际发布前仍应在目标环境重新执行检查和回滚验证。

## 能力概览

### 业务能力

| 能力 | 说明 |
| --- | --- |
| 用户与分组 | 注册、登录、退出、分组 CRUD、分组排序 |
| 短链管理 | 创建、批量创建、修改、分页查询、标题解析 |
| 跳转 | 校验短链状态和有效期，记录访问事件并返回 302 |
| 回收站 | 回收、分页查询、恢复、彻底删除 |
| 统计 | PV、UV、UIP、设备、浏览器、操作系统、网络、地域和访问记录 |
| 冷热数据 | 定时归档冻结链接，冷热库合并查询，支持冷数据回温 |
| 风控 | URL 内容检测、风险分类、自动封禁 |
| 通知 | 封禁事件生成站内通知，并通过 WebSocket 推送给在线用户 |
| 限流 | gateway、user-service、project-service 对注册、登录和短链创建进行限流 |

### 工程能力

- Java 17、Spring Boot 3.2、Spring Cloud Alibaba
- MyBatis-Plus 和 ShardingSphere 分库分表
- Redis、Redisson、Caffeine、本地缓存和 Bloom Filter
- RocketMQ 事件驱动和异步解耦
- Nacos 服务注册与发现
- Actuator、Micrometer、Prometheus 指标
- Docker Compose、GitHub Actions、GHCR 镜像发布

## 架构

```text
                         +----------------------+
                         |    console-vue       |
                         | Vue 3 + Element Plus |
                         +----------+-----------+
                                    |
                              /api 请求
                                    v
                         +----------------------+
                         |   gateway-service    |
                         | Spring Cloud Gateway |
                         | 鉴权、路由、限流      |
                         +----+-----------+-----+
                              |           |
                admin / project API       | stats API
                              |           |
             +----------------+           +----------------+
             v                                 v
   +----------------------+           +----------------------+
   |    user-service      |           |   project-service    |
   | 用户、分组、通知      |           | 创建、跳转、回收、缓存 |
   +----------+-----------+           +----------+-----------+
              | Feign / MQ                       | RocketMQ
              v                                   v
   +----------------------+           +----------------------+
   |    stats-service     |           |     risk-service     |
   | 统计消费与查询        |           | 风控检测与自动封禁    |
   +----------------------+           +----------+-----------+
                                                   |
                                                   | 通知事件
                                                   v
                                         user-service 通知中心

  基础设施：MySQL / ShardingSphere、Redis、RocketMQ、Nacos
  观测组件：Prometheus、Grafana、Alertmanager
```

生产环境还提供 `aggregation-service`。它把 user-service 和 project-service 的核心能力聚合到一个业务进程中，配合 `SPRING_PROFILES_ACTIVE=aggregation` 使用，减少生产环境需要维护的业务容器数量。

## 代码结构

```text
.
├── dependencies/                 # 统一依赖版本和 BOM
├── frameworks/                   # 可复用 Spring Boot starter
│   ├── base                     # 基础能力
│   ├── common                   # 通用组件
│   ├── web                      # Web、异常和响应封装
│   ├── database                 # 数据库和 MyBatis-Plus
│   ├── cache                    # Redis、Redisson、本地缓存
│   ├── distributedid            # 分布式 ID
│   ├── idempotent               # 幂等控制
│   └── ...
├── services/
│   ├── shortlink-api            # 跨服务 DTO、接口和事件契约
│   ├── user-service             # 用户、分组、通知和 WebSocket
│   ├── project-service          # 短链核心业务和跳转
│   ├── stats-service            # 统计消息消费和统计查询
│   ├── risk-service             # 风险检测和封禁
│   ├── gateway-service          # 网关、鉴权、路由和限流
│   └── aggregation-service      # 生产聚合服务
├── console-vue/                 # Vue 3 管理控制台
├── docker/                      # 本地依赖、生产编排、监控和 Nginx
├── deploy/                      # 服务器初始化和部署脚本
├── resources/database/          # 数据库初始化 SQL
├── tests/performance/           # JMeter 压测脚本
└── docs/                        # 设计、实施、验收和回滚文档
```

## 服务和端口

| 服务 | 默认端口 | 主要职责 |
| --- | ---: | --- |
| `gateway-service` | `8000` | 网关路由、鉴权、限流、WebSocket 转发 |
| `project-service` | `8001` | 短链创建、修改、跳转、回收站、冷热数据 |
| `user-service` | `8002` | 用户、分组、通知和 WebSocket |
| `aggregation-service` | `8003` | 聚合 user/project 能力，生产部署使用 |
| `stats-service` | `8004` | 统计事件消费和统计查询 |
| `risk-service` | `8005` | URL 风险检测和封禁 |

每个业务服务都暴露以下 Actuator 端点：

```text
GET /actuator/health
GET /actuator/prometheus
```

## 环境要求

本地开发需要：

- JDK 17
- Maven 3.9+
- Node.js 18+
- npm
- Docker Engine 或 Docker Desktop
- 至少 4 GB 可用内存（完整依赖栈和多个 Java 服务会同时运行）

如果使用 WSL，建议把仓库放在 WSL 原生文件系统，而不是 `/mnt/c` 或 `/mnt/d`。Maven、Java 编译和 `node_modules` 会产生大量小文件，在 Windows 挂载盘上通常明显更慢。

## 本地开发

### 1. 启动基础依赖

本地拆分开发只需要先启动 MySQL、Redis、Nacos 和 RocketMQ：

```bash
docker compose -f docker/docker-compose.yml \
  up -d mysql redis nacos namesrv broker
```

查看容器状态：

```bash
docker compose -f docker/docker-compose.yml ps
```

首次启动 MySQL 时，`resources/database/link.sql` 会通过初始化脚本导入 `link` 和 `link_cold` 数据库及表结构。初始化只对新建的数据卷生效。

需要本地监控或 RocketMQ Dashboard 时，再启动可选组件：

```bash
docker compose -f docker/docker-compose.yml \
  up -d dashboard prometheus grafana alertmanager
```

### 2. 编译后端

只构建日常开发需要的服务：

```bash
mvn -pl services/gateway-service,services/user-service,services/project-service,services/stats-service,services/risk-service \
  -am -DskipTests package
```

只构建一个服务：

```bash
mvn -pl services/project-service -am -DskipTests package
```

项目的 `compile` 阶段会执行 Spotless；开发循环中不需要每次都跑全量测试。提交前再执行完整检查即可。

### 3. 启动后端

本地开发建议使用拆分模式，启动 gateway、user、project、stats 和 risk：

```bash
mvn -pl services/gateway-service -am \
  -Dmaven.test.skip=true -Dskip.checkstyle.check=true spring-boot:run

mvn -pl services/user-service -am \
  -Dmaven.test.skip=true -Dskip.checkstyle.check=true spring-boot:run

mvn -pl services/project-service -am \
  -Dmaven.test.skip=true -Dskip.checkstyle.check=true spring-boot:run

mvn -pl services/stats-service -am \
  -Dmaven.test.skip=true -Dskip.checkstyle.check=true spring-boot:run

mvn -pl services/risk-service -am \
  -Dmaven.test.skip=true -Dskip.checkstyle.check=true spring-boot:run
```

也可以直接在 IDE 中运行各模块的 `*Application` 启动类。启动前请确认基础依赖已经可连接。

如果只调试 project-service，可以覆盖端口：

```bash
mvn -pl services/project-service -am spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8011"
```

### 4. 启动前端

```bash
cd console-vue
npm ci
npm run dev
```

Vite 开发服务器默认在 `http://localhost:5173`，`/api` 请求会代理到 `http://127.0.0.1:8000`。因此前端开发时至少需要启动 gateway，以及 gateway 路由到的业务服务。

前端常用命令：

```bash
npm run dev       # 开发服务器
npm run build     # 生产构建
npm run lint      # ESLint 检查（当前脚本会自动修复）
npm run preview   # 预览生产构建
```

### 5. 本地入口

| 入口 | 地址 |
| --- | --- |
| Vue 控制台 | `http://localhost:5173` |
| gateway | `http://localhost:8000` |
| project-service | `http://localhost:8001` |
| user-service | `http://localhost:8002` |
| aggregation-service | `http://localhost:8003` |
| stats-service | `http://localhost:8004` |
| risk-service | `http://localhost:8005` |
| RocketMQ Dashboard | `http://localhost:8080` |
| Nacos | `http://localhost:8848/nacos` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` |
| Alertmanager | `http://localhost:9093` |

Grafana 默认账号是 `admin / admin`，仅适用于本地环境。

## API 路由概览

通过 gateway 访问时，主要路由如下：

| 路径 | 用途 | 目标服务 |
| --- | --- | --- |
| `/api/short-link/admin/v1/user/**` | 注册、登录、用户信息 | user-service 或 aggregation-service |
| `/api/short-link/admin/v1/group/**` | 分组管理 | user-service 或 aggregation-service |
| `/api/short-link/admin/v1/notification/**` | 通知列表、未读数、已读 | user-service 或 aggregation-service |
| `/api/short-link/v1/create` | 创建短链 | project-service 或 aggregation-service |
| `/api/short-link/v1/update` | 修改短链 | project-service 或 aggregation-service |
| `/api/short-link/v1/page` | 短链分页查询 | project-service 或 aggregation-service |
| `/api/short-link/v1/recycle-bin/**` | 回收站操作 | project-service 或 aggregation-service |
| `/api/short-link/v1/stats/**` | 统计查询 | stats-service |
| `/api/short-link/admin/v1/notification/ws` | 通知 WebSocket | user-service 或 aggregation-service |

短链跳转使用短 URI：

```text
GET /{short-uri}
```

本地拆分模式下可以直接访问 project-service；生产模式下由 Nginx 把非 `/api/` 请求转发到 aggregation-service。

## 配置

开发环境默认连接本机依赖服务。生产或 Docker 部署通过环境变量覆盖配置，模板见 [`docker/.env.example`](docker/.env.example)。常用变量：

| 变量 | 作用 | 示例 |
| --- | --- | --- |
| `DATABASE_ENV` | ShardingSphere 配置环境 | `dev` / `prod` |
| `SPRING_PROFILES_ACTIVE` | Spring profile，生产通常使用 aggregation | `aggregation` |
| `SHORT_LINK_DOMAIN_DEFAULT` | 生成短链使用的域名 | `s.example.com` |
| `SHORT_LINK_DOMAIN_PROTOCOL` | 生成短链使用的协议 | `http` / `https` |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | 不要提交真实值 |
| `REDIS_PASSWORD` | Redis 密码 | 不要提交真实值 |
| `SHORT_LINK_STATS_LOCALE_AMAP_KEY` | 地域统计 API Key | 按需配置 |
| `DASHSCOPE_API_KEY` | 风控模型 API Key | 按需配置 |
| `GHCR_USERNAME` / `GHCR_TOKEN` | 拉取私有 GHCR 镜像 | 仅部署时配置 |

不要把生产密码、SSH 私钥或第三方 API Key 写入仓库。GitHub Actions 使用的值应配置在仓库 Secrets 中。

短链域名必须配置成用户实际访问的域名。不要在生产环境继续使用 `nurl.ink:8001` 或 `nurl.ink:8003` 这类本地联调地址。

## 测试和质量检查

后端测试按服务拆分，测试代码位于各服务的 `src/test` 目录。常用命令：

```bash
# 全量测试
mvn test

# 跳过 Checkstyle 的全量测试
mvn test -Dskip.checkstyle.check=true

# 只测试指定服务
mvn -pl services/project-service -Dskip.checkstyle.check=true test
mvn -pl services/user-service -Dskip.checkstyle.check=true test

# 只做快速编译，不编译和执行测试
mvn -pl services/project-service -am \
  -Dmaven.test.skip=true -Dskip.checkstyle.check=true package
```

前端检查：

```bash
cd console-vue
npm run lint
npm run build
```

提交前建议至少完成：

1. 目标服务的单元/API 测试；
2. 相关服务的编译和启动检查；
3. gateway 到业务服务的最小黑盒链路；
4. 前端 `npm run build`；
5. `git diff --check` 和 `git status --short`。

测试依赖真实 Redis、MySQL、Nacos 或 RocketMQ 时，要先启动 Docker 基础设施。没有启动依赖时，测试失败不能简单标记为代码问题，也不能把跳过测试当成通过。

## Docker 和生产部署

Docker 配置分为三部分：

| 文件 | 用途 |
| --- | --- |
| `docker/docker-compose.yml` | 本地依赖和监控栈，映射宿主机端口 |
| `docker/docker-compose.deploy.yml` | 生产基础设施，不对外暴露大部分依赖端口 |
| `docker/docker-compose.app.yml` | 生产业务容器：frontend、gateway、aggregation、stats、risk |

生产应用编排只发布 frontend 的 80 端口；gateway 和 aggregation 仅通过 Compose 内部网络访问，避免绕过 Nginx 的可信客户端地址处理。

### 本地构建镜像

```bash
docker build -f docker/Dockerfile.backend \
  --build-arg JAR_FILE=services/aggregation-service/target/shortlink-aggregation-service.jar \
  -t shortlink-aggregation-service:local .

docker build -f docker/Dockerfile.frontend \
  -t shortlink-frontend:local .
```

GitHub Actions 在推送到 `main` 时执行 [`build.yml`](.github/workflows/build.yml)：

- 构建 gateway、aggregation、stats、risk 四个后端镜像；
- 构建 Vue 前端镜像；
- 推送 `latest` 和提交 SHA 两种标签到 GHCR。

### 服务器部署

部署工作流 [`deploy.yml`](.github/workflows/deploy.yml) 通过 GitHub Actions 手动触发。它会把部署文件复制到服务器 `/opt/shortlink`，然后执行：

```bash
bash deploy/setup-server.sh full   # 首次部署：基础设施 + 应用
bash deploy/setup-server.sh infra  # 只更新基础设施
bash deploy/setup-server.sh app    # 日常只更新应用
```

服务器只需要预装 Docker 和 Docker Compose。应用入口为：

- `http://<server>/console/`：控制台
- `http://<server>/api/`：API，经 Nginx 转发到 gateway
- `http://<server>/<short-uri>`：短链跳转，经 Nginx 转发到 aggregation

当前部署方式是单机容器切换，不是蓝绿发布或滚动发布。`app` 部署会重建应用容器，并存在短暂切换窗口。正式发布前要用历史 SHA 镜像完成一次回滚验证。

## 监控和验收

监控配置位于 [`docker/monitoring`](docker/monitoring)：

- Prometheus：抓取 `/actuator/prometheus`；
- Grafana：预置 `ShortLink Overview` 仪表盘；
- Alertmanager：接收服务不可用、HTTP 5xx、P95 延迟、MQ 消费失败和冷热迁移失败告警。

常见检查命令：

```bash
curl http://localhost:8000/actuator/health
curl http://localhost:8001/actuator/health
curl http://localhost:8002/actuator/health
curl http://localhost:8003/actuator/health
curl http://localhost:8004/actuator/health
curl http://localhost:8005/actuator/health
```

Phase 7 预检脚本：

```bash
bash docker/monitoring/scripts/phase7-preflight.sh
```

脚本依赖业务服务和监控容器已经启动。详细的发布前检查、回滚步骤和历史验收记录见：

- [`docs/refactor/phase7-release-checklist.md`](docs/refactor/phase7-release-checklist.md)
- [`docs/refactor/phase7-rollback-playbook.md`](docs/refactor/phase7-rollback-playbook.md)
- [`docs/refactor/phase7-acceptance-report-2026-03-06.md`](docs/refactor/phase7-acceptance-report-2026-03-06.md)

## 性能测试

JMeter 脚本位于 [`tests/performance/jmeter`](tests/performance/jmeter)：

- `create-short-link.jmx`
- `redirect-short-link.jmx`

压测前需要明确目标服务、数据库规模、缓存状态、并发数和数据清理策略。不要把开发环境单次冒烟结果当成容量结论。

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [`CODE_STYLE.md`](CODE_STYLE.md) | 编码和提交规范 |
| [`docs/refactor/requirement.md`](docs/refactor/requirement.md) | 重构需求规格 |
| [`docs/plans/2026-02-16-project-service-refactor-design.md`](docs/plans/2026-02-16-project-service-refactor-design.md) | project-service 拆分设计和阶段记录 |
| [`docs/plans/2026-03-21-cicd-automated-deployment-implementation-plan.md`](docs/plans/2026-03-21-cicd-automated-deployment-implementation-plan.md) | CI/CD 实施计划 |
| [`docs/plans/2026-03-07-notification-bell-design.md`](docs/plans/2026-03-07-notification-bell-design.md) | 通知铃铛设计 |
| [`docs/refactor/phase7-release-checklist.md`](docs/refactor/phase7-release-checklist.md) | 发布检查清单 |
| [`docs/refactor/phase7-rollback-playbook.md`](docs/refactor/phase7-rollback-playbook.md) | 回滚操作手册 |

## 提交约定

项目使用 Conventional Commits 风格：

```text
feat(scope): add a capability
fix(scope): correct a defect
refactor(scope): restructure code without behavior change
test(scope): add or update tests
docs(scope): update documentation
chore(scope): maintain tooling or deployment
```

## License

仓库源码使用 Apache 2.0 风格版权头。具体版权文本见 [`format/copyright.txt`](format/copyright.txt)。
