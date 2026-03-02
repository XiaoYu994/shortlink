# Phase 7 Release & Acceptance Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete Phase 7 by landing observability (Prometheus/Grafana/Alertmanager), release gates, rollback playbook, and acceptance checklist for all refactored services.

**Architecture:** Add a unified metrics surface on all six services (`/actuator/prometheus`), then attach an external monitoring stack in Docker. Roll out in layers: app metrics first, scrape/visualization second, alerting and release acceptance last.

**Tech Stack:** Spring Boot Actuator, Micrometer Prometheus registry, Prometheus, Grafana, Alertmanager, Docker Compose, Maven, JUnit 5.

---

### Task 1: Enable Actuator + Prometheus Dependencies

**Files:**
- Modify: `services/project-service/pom.xml`
- Modify: `services/user-service/pom.xml`
- Modify: `services/stats-service/pom.xml`
- Modify: `services/risk-service/pom.xml`
- Modify: `services/gateway-service/pom.xml`
- Modify: `services/aggregation-service/pom.xml`

**Step 1: Add `spring-boot-starter-actuator` to all six service POMs**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Step 2: Add `micrometer-registry-prometheus` to all six service POMs**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**Step 3: Run compile-only verification per changed module**

Run: `mvn -pl services/project-service,services/user-service,services/stats-service,services/risk-service,services/gateway-service,services/aggregation-service -am -DskipTests compile`  
Expected: `BUILD SUCCESS`

**Step 4: Fix dependency ordering/style issues (if any)**

Run: `mvn -pl services -am -DskipTests compile`  
Expected: `BUILD SUCCESS`

**Step 5: Commit**

```bash
git add services/*/pom.xml
git commit -m "feat(phase7): add actuator and prometheus registry dependencies"
```

### Task 2: Expose Unified Metrics Endpoints in Service Config

**Files:**
- Modify: `services/project-service/src/main/resources/application.yaml`
- Modify: `services/user-service/src/main/resources/application.yaml`
- Modify: `services/stats-service/src/main/resources/application.yaml`
- Modify: `services/risk-service/src/main/resources/application.yaml`
- Modify: `services/gateway-service/src/main/resources/application.yaml`
- Modify: `services/aggregation-service/src/main/resources/application.yaml`

**Step 1: Add endpoint exposure block to each service**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5,0.9,0.95,0.99
```

**Step 2: Keep existing business config untouched**

Do not move or rename existing service keys (`short-link`, `rocketmq`, `sa-token`, etc.).

**Step 3: Validate YAML syntax**

Run: `mvn -pl services -am -DskipTests compile`  
Expected: `BUILD SUCCESS`

**Step 4: Manual endpoint smoke check for one service**

Run service locally, then: `curl http://127.0.0.1:8001/actuator/prometheus`  
Expected: text output including `jvm_` and `http_server_requests` metrics.

**Step 5: Commit**

```bash
git add services/*/src/main/resources/application.yaml
git commit -m "feat(phase7): expose unified actuator prometheus endpoints"
```

### Task 3: Add Monitoring Stack Configuration Files

**Files:**
- Create: `docker/monitoring/prometheus.yml`
- Create: `docker/monitoring/alert.rules.yml`
- Create: `docker/monitoring/alertmanager.yml`
- Create: `docker/monitoring/grafana/provisioning/datasources/datasource.yml`
- Create: `docker/monitoring/grafana/provisioning/dashboards/dashboard.yml`
- Create: `docker/monitoring/grafana/dashboards/shortlink-overview.json`

**Step 1: Create `prometheus.yml` with six scrape jobs**

Targets:
- `project-service:8001`
- `user-service:8002`
- `aggregation-service:8003`
- `stats-service:8004`
- `risk-service:8005`
- `gateway-service:8000`

Path: `/actuator/prometheus`

**Step 2: Create initial alert rules**

Minimum rule set:
- ServiceDown (target down)
- HighHttp5xxRate
- HighP95Latency
- RocketMQConsumerFailureSpike

**Step 3: Create Grafana provisioning and dashboard JSON**

Dashboard panels (minimum):
- Requests/sec
- Error ratio
- P95 latency
- JVM memory and CPU
- MQ consume success/fail counters

**Step 4: Validate Prometheus config format**

Run: `docker run --rm -v $(pwd)/docker/monitoring/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus:v2.53.0 --config.file=/etc/prometheus/prometheus.yml --web.listen-address=:9091`  
Expected: process starts without config parse errors.

**Step 5: Commit**

```bash
git add docker/monitoring
git commit -m "feat(phase7): add prometheus grafana alertmanager configs"
```

### Task 4: Wire Monitoring Services into Compose

**Files:**
- Modify: `docker/docker-compose.yml`

**Step 1: Add Prometheus service**

Use `prom/prometheus` image and mount:
- `./monitoring/prometheus.yml`
- `./monitoring/alert.rules.yml`

Expose port `9090`.

**Step 2: Add Alertmanager service**

Use `prom/alertmanager` image, mount `./monitoring/alertmanager.yml`, expose `9093`.

**Step 3: Add Grafana service**

Use `grafana/grafana` image, mount provisioning and dashboards, expose `3000`.

**Step 4: Validate compose file**

Run: `docker compose -f docker/docker-compose.yml config`  
Expected: normalized compose output without errors.

**Step 5: Commit**

```bash
git add docker/docker-compose.yml
git commit -m "feat(phase7): integrate monitoring stack into docker compose"
```

### Task 5: Add Business Metrics in Project Service (TDD)

**Files:**
- Create: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/metrics/ShortLinkMetrics.java`
- Modify: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/service/impl/ShortLinkCoreServiceImpl.java`
- Modify: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/service/impl/ShortLinkRedirectServiceImpl.java`
- Modify: `services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/job/ShortLinkColdMigrationJob.java`
- Test: `services/project-service/src/test/java/com/xhy/shortlink/biz/projectservice/metrics/ShortLinkMetricsTest.java`

**Step 1: Write failing unit tests for counter/timer registration**

Test cases:
- `should_increment_create_success_counter`
- `should_increment_redirect_error_counter`
- `should_record_redirect_latency_timer`

**Step 2: Run tests to verify they fail**

Run: `mvn -pl services/project-service -am -Dtest=ShortLinkMetricsTest test`  
Expected: FAIL (missing metrics component).

**Step 3: Implement `ShortLinkMetrics` wrapper**

Use `MeterRegistry` and provide methods:
- `recordCreateSuccess()`
- `recordCreateFailure()`
- `recordRedirectSuccess(Duration duration)`
- `recordRedirectFailure(Duration duration)`
- `recordColdMigrationBatch(int size)`

**Step 4: Hook metrics into service and job code paths, rerun tests**

Run: `mvn -pl services/project-service -am -Dtest=ShortLinkMetricsTest test`  
Expected: PASS

**Step 5: Commit**

```bash
git add services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/metrics \
        services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/service/impl/ShortLinkCoreServiceImpl.java \
        services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/service/impl/ShortLinkRedirectServiceImpl.java \
        services/project-service/src/main/java/com/xhy/shortlink/biz/projectservice/job/ShortLinkColdMigrationJob.java \
        services/project-service/src/test/java/com/xhy/shortlink/biz/projectservice/metrics/ShortLinkMetricsTest.java
git commit -m "feat(phase7): add project-service business metrics"
```

### Task 6: Add MQ and Stats Metrics in Stats/Risk Services (TDD)

**Files:**
- Create: `services/stats-service/src/main/java/com/xhy/shortlink/biz/statsservice/metrics/StatsMetrics.java`
- Modify: `services/stats-service/src/main/java/com/xhy/shortlink/biz/statsservice/mq/consumer/ShortLinkStatsSaveConsumer.java`
- Test: `services/stats-service/src/test/java/com/xhy/shortlink/biz/statsservice/metrics/StatsMetricsTest.java`
- Create: `services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/metrics/RiskMetrics.java`
- Modify: `services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/mq/consumer/ShortLinkRiskCheckConsumer.java`
- Modify: `services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/mq/consumer/ShortLinkViolationNotifyConsumer.java`
- Test: `services/risk-service/src/test/java/com/xhy/shortlink/biz/riskservice/metrics/RiskMetricsTest.java`

**Step 1: Write failing tests for consume success/failure counters and latency timers**

Required metric names:
- `shortlink_mq_consume_success_total`
- `shortlink_mq_consume_failure_total`
- `shortlink_mq_consume_latency`

**Step 2: Run tests and confirm failure**

Run: `mvn -pl services/stats-service,services/risk-service -am -Dtest=StatsMetricsTest,RiskMetricsTest test`  
Expected: FAIL

**Step 3: Implement metrics wrappers and consumer integration**

Record:
- success/failure count
- processing duration per message

**Step 4: Rerun targeted tests**

Run: `mvn -pl services/stats-service,services/risk-service -am -Dtest=StatsMetricsTest,RiskMetricsTest test`  
Expected: PASS

**Step 5: Commit**

```bash
git add services/stats-service/src/main/java/com/xhy/shortlink/biz/statsservice/metrics \
        services/stats-service/src/main/java/com/xhy/shortlink/biz/statsservice/mq/consumer/ShortLinkStatsSaveConsumer.java \
        services/stats-service/src/test/java/com/xhy/shortlink/biz/statsservice/metrics/StatsMetricsTest.java \
        services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/metrics \
        services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/mq/consumer/ShortLinkRiskCheckConsumer.java \
        services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/mq/consumer/ShortLinkViolationNotifyConsumer.java \
        services/risk-service/src/test/java/com/xhy/shortlink/biz/riskservice/metrics/RiskMetricsTest.java
git commit -m "feat(phase7): add mq consume metrics for stats and risk services"
```

### Task 7: Add Release, Rollback, and Acceptance Runbooks

**Files:**
- Create: `docs/refactor/phase7-release-checklist.md`
- Create: `docs/refactor/phase7-rollback-playbook.md`
- Create: `docs/refactor/phase7-acceptance-report-template.md`
- Modify: `docs/plans/2026-02-16-project-service-refactor-design.md`

**Step 1: Write release checklist**

Include:
- preflight gate
- rollout sequence
- post-deploy verification

**Step 2: Write rollback playbook**

Include:
- monitor stack rollback
- single-service rollback
- incident evidence collection

**Step 3: Write acceptance report template**

Include:
- key metrics snapshot
- alert summary
- go/no-go decision

**Step 4: Update refactor progress table**

Set Phase 6 status to in-progress/complete as appropriate and add Phase 7 row.

**Step 5: Commit**

```bash
git add docs/refactor/phase7-release-checklist.md \
        docs/refactor/phase7-rollback-playbook.md \
        docs/refactor/phase7-acceptance-report-template.md \
        docs/plans/2026-02-16-project-service-refactor-design.md
git commit -m "docs(phase7): add release rollback acceptance runbooks"
```

### Task 8: End-to-End Verification and Evidence Collection

**Files:**
- Modify: `docs/refactor/phase7-acceptance-report-template.md` (fill execution evidence)

**Step 1: Bring up dependencies and monitoring stack**

Run: `docker compose -f docker/docker-compose.yml up -d`  
Expected: Prometheus/Grafana/Alertmanager containers healthy.

**Step 2: Start all six services and run smoke traffic**

Generate sample create/redirect/stats traffic for 5-10 minutes.

**Step 3: Validate monitoring behavior**

Checks:
- Prometheus targets all UP
- Grafana dashboard has non-empty time series
- test alert can fire and recover

**Step 4: Run final test command set**

Run: `mvn -pl services -am test`  
Expected: `BUILD SUCCESS`

**Step 5: Commit verification evidence**

```bash
git add docs/refactor/phase7-acceptance-report-template.md
git commit -m "test(phase7): record observability and release acceptance evidence"
```

