# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a distributed short link service built with Spring Cloud microservices architecture. The system provides URL shortening, statistics tracking, risk control, and cold data migration capabilities.

**Tech Stack:**
- Java 17
- Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / Spring Cloud Alibaba 2023.0.1.0
- MyBatis-Plus 3.5.15 + ShardingSphere 5.5.0 (database sharding)
- Redis (caching, distributed locks, message queue)
- RocketMQ (alternative message queue implementation)
- Nacos (service discovery and configuration)
- Vue 3 + Vite (frontend)

## Module Architecture

### 1. **user-service** (Port 8002)
User and group management service.
- User registration, login, authentication
- Short link group (folder) management
- User flow rate limiting via Lua scripts
- OpenFeign client to call project service

### 2. **project-service** (Port 8001)
Core short link business logic service.
- Short link creation with bloom filter or distributed lock
- URL redirection with multi-level caching (local cache → Redis → database)
- Statistics collection via message queue (Redis Stream or RocketMQ)
- Risk control for malicious URLs (async via MQ)
- Cold data migration (links inactive for 90+ days moved to separate database)
- Expired link grace period handling

### 3. **gateway-service** (Port 8000)
Spring Cloud Gateway for routing and token validation.
- Routes requests to user/project services based on path
- Token validation filter checks Redis for valid user sessions
- Bypasses authentication for login/register endpoints

### 4. **aggregation-service** (Port 8003)
Aggregated deployment module that combines user, project, stats, and risk services into a single deployable unit for low-memory production hosts. Gateway stays separate because it is WebFlux.

### 5. **console-vue**
Vue 3 frontend application for the short link management console.

## Database Sharding Strategy

ShardingSphere configuration splits data across 16 tables:
- `t_link_0` to `t_link_15` (sharded by `gid` - group ID)
- `t_link_goto_0` to `t_link_goto_15` (sharded by `full_short_url`)
- `t_group_0` to `t_group_15` (sharded by `username`)
- Separate `link_cold` database for archived cold data

## Message Queue Architecture

The system supports **two MQ implementations** (configurable via `short-link.message-queue.implement`):

### Redis-based (default for dev)
- Uses Redis Streams for event publishing
- Consumers: `ShortLinkCacheRedisConsumer`, `ShortLinkRiskRedisConsumer`, `ShortLinkViolationNotifyRedisConsumer`
- Producers: `ShortLinkCacheRedisProducer`, `ShortLinkRiskRedisProducer`, `ShortLinkSaveRedisProducer`

### RocketMQ-based (production)
- Uses RocketMQ topics for event publishing
- Consumers: `ShortLinkCacheRocketMQConsumer`, `ShortLinkRiskRocketMQConsumer`, `ShortLinkViolationNotifyRocketMQConsumer`
- Producers: `ShortLinkCacheRocketMQProducer`, `ShortLinkRiskRocketMQProducer`, `ShortLinkStaticSaveRocketMQProducer`

**Key Events:**
- `ShortLinkStatsRecordEvent` - Statistics recording
- `ShortLinkRiskEvent` - URL risk checking
- `ShortLinkViolationEvent` - Violation notifications

## Caching Strategy

Multi-level caching for short link redirection:
1. **Local Caffeine Cache** - First level, fastest
2. **Redis Cache** - Second level, shared across instances
   - Format: `validDate|originalUrl|gid` (optimized to avoid DB queries during stats collection)
3. **Database** - Final fallback with ShardingSphere routing

**Cache Invalidation:**
- Updates/deletes trigger cache eviction via MQ
- Redis Pub/Sub notifies all instances to clear local cache

## Statistics Architecture

**Real-time Stats Collection:**
- Uses HyperLogLog (12KB memory) for UV/UIP deduplication at billion-scale
- Redis ZSet stores today's click rankings for pagination
- Async processing via MQ to avoid blocking redirect requests
- Lua scripts for atomic rank updates

**Stats Tables:**
- `link_access_logs` - Detailed access logs
- `link_access_stats` - Daily aggregated stats
- `link_browser_stats`, `link_os_stats`, `link_device_stats`, `link_network_stats` - Dimension stats
- `link_locale_stats` - Geographic stats (uses Amap API for IP geolocation)

## Cold Data Migration

Automated job (`ShortLinkColdDataMigrationJob`) runs daily at 2:30 AM:
- Migrates links inactive for 90+ days to `link_cold` database
- Maintains hot data in main database for performance
- Re-heating mechanism: if cold link gets 1000+ clicks, moves back to hot database
- Expired links have 30-day grace period before freezing

## Build and Run Commands

### Backend Services

```bash
# Build all modules
mvn clean package -DskipTests

# Run individual services (from project root)
mvn -pl services/user-service -am spring-boot:run
mvn -pl services/project-service -am spring-boot:run
mvn -pl services/gateway-service -am spring-boot:run
mvn -pl services/aggregation-service -am spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=UserTableShardingTest
```

### Frontend

```bash
cd console-vue

# Install dependencies
npm install

# Run dev server (http://localhost:5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Lint and fix
npm run lint

# Format code
npm run format
```

## Configuration Profiles

Services support multiple profiles via `spring.profiles.active`:
- **dev** - Local development (uses `shardingsphere-config-dev.yaml`)
- **prod** - Production (uses `shardingsphere-config-prod.yaml`)
- **aggregation** - Aggregated deployment mode

## Key Configuration Properties

### Short Link Domain
```yaml
short-link:
  domain:
    default: smallfish.cloud  # Production short-link domain; the frontend console is served under /console/
```

### Message Queue Selection
```yaml
short-link:
  message-queue:
    implement: RocketMQ  # or Redis
```

### Cold Data Migration
```yaml
short-link:
  cold-data:
    enabled: true
    days: 90              # Days of inactivity before migration
    batch-size: 200
    cron: "0 30 2 * * ?"  # Daily at 2:30 AM
    rehot:
      threshold: 1000     # Clicks needed to move back to hot database
```

### Flow Rate Limiting (Admin)
```yaml
short-link:
  flow-limit:
    enable: true
    time-window: 1        # seconds
    max-access-count: 20  # max requests per window
```

## Important Implementation Details

### Short Link Generation
Two strategies available:
1. **Bloom Filter** (`createShortlink`) - Fast, probabilistic collision detection
2. **Distributed Lock** (`createShortLinkByLock`) - Slower but guaranteed uniqueness

### URL Redirection Flow
1. Extract short URI from request path
2. Check local Caffeine cache
3. Check Redis cache (format: `validDate|originalUrl|gid`)
4. Query `t_link_goto` table (routing table) to get `gid`
5. Query `t_link` table with `gid` for full link details
6. Check if link is valid (not expired, not deleted)
7. Async send stats event to MQ
8. Redirect to original URL

### Risk Control
- Async URL risk checking via MQ after link creation
- Uses external API or AI service for malicious URL detection
- Violation notifications sent to user via `user_notification` table

### Idempotency Handling
- `MessageQueueIdempotentHandler` prevents duplicate message processing
- Uses Redis SET with expiration for deduplication keys

## Testing

JMeter test plans included:
- `tests/performance/jmeter/create-short-link.jmx` - Short link creation load test
- `tests/performance/jmeter/redirect-short-link.jmx` - Redirection load test

## Common Gotchas

1. **ShardingSphere Configuration**: Ensure `database.env` property matches your profile (dev/prod)
2. **Redis Connection**: All services share Redis, ensure it's running before starting services
3. **Nacos Discovery**: Services register with Nacos, ensure Nacos server is accessible
4. **Message Queue**: When switching between Redis/RocketMQ, ensure corresponding infrastructure is running
5. **Database Sharding**: Table names must match ShardingSphere actual nodes configuration
6. **Cold Data Migration**: Job only runs when `short-link.cold-data.enabled=true`
7. **Aggregation config**: aggregation-service does not auto-load child service `application.yaml`. Auth, Feign targets, stats and risk settings must be declared in aggregation's own yaml or in Nacos (`shortlink-common.yaml` / `shortlink-aggregation-service.yaml`).
8. **Nacos config**: production apps load shared config from Nacos. `.env` only keeps infrastructure secrets (MySQL/Redis/Nacos auth). Do not put DASHSCOPE/AMAP/Sa-Token in Compose app environment.
9. **Optional middleware**: `MANAGE_MYSQL/REDIS/NACOS/ROCKETMQ` default true for one-click deploy. Set false to reuse existing services. App deploys must not delete unmanaged infra; use `infra-reset` only for managed containers.

## Service Dependencies

Start order for local development:
1. MySQL (with `link` and `link_cold` databases)
2. Redis
3. Nacos
4. RocketMQ (if using RocketMQ mode)
5. Gateway → Aggregation (production) or user + project + stats + risk (split mode)
6. Frontend (console-vue)

## Model Usage Preferences

When using Trellis workflow with Task tool, prefer the following model selection strategy:

| Task Type | Preferred Model | Reason |
|-----------|----------------|--------|
| Code analysis, architecture design | `opus` | Requires deep understanding and reasoning |
| Code implementation, refactoring | `sonnet` | Best balance of quality and cost (default) |
| Code review, format checking | `haiku` | Fast and cost-effective |
| Simple fixes, documentation | `haiku` | Sufficient for straightforward tasks |

**Cost Optimization**:
- Use `opus` only for complex analysis tasks
- Use `sonnet` for most development work (recommended default)
- Use `haiku` for simple, repetitive tasks

## Refactoring Analysis (2026-02-06)

### Current Status

**Completed Refactoring**:
- ✅ `dependencies` - Unified dependency management BOM module
- ✅ `code-style` - Checkstyle and Spotless code standards

**Branch**: `feature/refactor-frameworks`

## Git Commit Rules

- Do NOT include `Co-Authored-By` lines in commit messages
- Follow conventional commits format: `type(scope): description`
