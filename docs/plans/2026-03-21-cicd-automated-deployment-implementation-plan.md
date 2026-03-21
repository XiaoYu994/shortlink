# CI/CD Automated Deployment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a production deployment pipeline that builds and ships the current ShortLink system to a VM on every push to `main`, without dropping existing stats or risk-control behavior.

**Architecture:** Keep the current business split intact for production: `frontend + gateway-service + aggregation-service + stats-service + risk-service`. The gateway gets a dedicated `aggregation` routing profile so admin and project APIs flow to `shortlink-aggregation-service`, while stats stays a separate service and risk stays an internal MQ consumer. Build backend JARs once in GitHub Actions, package them into runtime images stored in GHCR, and deploy with a fixed Docker Compose project so infrastructure and application services share one network.

**Tech Stack:** GitHub Actions, Maven, Node.js/Vite, Docker, Docker Compose, GHCR, Nginx, Spring Boot, Nacos, RocketMQ, MySQL, Redis

---

## Assumptions

- Production keeps current functionality complete. This means the deployment must include `services/aggregation-service`, `services/gateway-service`, `services/stats-service`, `services/risk-service`, and `console-vue`.
- Short-link redirect traffic remains served by `aggregation-service` on port `8003` for the first rollout. The console frontend is served on port `80` by Nginx and proxies `/api` to the gateway.
- Deployment uses a single Compose project name, for example `shortlink`, so app services can resolve infrastructure services by Compose service name (`mysql`, `redis`, `nacos`, `namesrv`).
- GHCR packages may be private. GitHub Actions can push with `GITHUB_TOKEN`, but the VM needs separate GHCR pull credentials.

## Approaches Considered

1. Three-app deployment (`frontend + gateway + aggregation`)

Rejected. Current code still depends on `stats-service` for monitoring queries and `risk-service` for async risk-control consumption. This would ship a broken production topology.

2. Five-app deployment with each Dockerfile running Maven internally

Works, but it rebuilds the same multi-module project several times and makes CI slower than necessary.

3. Five-app deployment with CI-built JARs and a shared backend runtime Dockerfile

Recommended. This keeps the topology correct, avoids duplicate Maven work, and reduces Dockerfile duplication.

### Task 1: Add a gateway profile for aggregation deployment

**Files:**
- Create: `services/gateway-service/src/main/resources/application-aggregation.yaml`
- Modify: `services/gateway-service/src/main/resources/application.yaml`

**Step 1: Create the aggregation routing profile**

Add a new profile file that routes:

- `/api/short-link/admin/**` -> `lb://shortlink-aggregation-service`
- `/api/short-link/**` -> `lb://shortlink-aggregation-service`
- `/api/short-link/v1/stats/**` -> `lb://shortlink-stats-service`

Preserve the current token filter behavior and white-list entries used by login and username check.

**Step 2: Keep the base gateway config environment-driven**

Update the base gateway config so production can be driven by environment variables:

- `SPRING_PROFILES_ACTIVE` default remains local-safe (`dev`)
- `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`
- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`
- `SPRING_CLOUD_NACOS_DISCOVERY_USERNAME`
- `SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD`

The deployment target must set `SPRING_PROFILES_ACTIVE=aggregation`.

**Step 3: Verify the gateway still packages**

Run:

```bash
mvn -Dmaven.repo.local=/tmp/.m2 -pl services/gateway-service -am -DskipTests package
```

Expected:

- Build succeeds
- The packaged artifact is `services/gateway-service/target/shortlink-gateway-service.jar`

**Step 4: Commit**

```bash
git add services/gateway-service/src/main/resources/application.yaml \
        services/gateway-service/src/main/resources/application-aggregation.yaml
git commit -m "feat: add gateway aggregation deployment profile"
```

### Task 2: Make backend runtime configuration production-safe for containers

**Files:**
- Modify: `services/aggregation-service/src/main/resources/application.yaml`
- Modify: `services/stats-service/src/main/resources/application.yaml`
- Modify: `services/risk-service/src/main/resources/application.yaml`
- Create: `services/stats-service/src/main/resources/shardingsphere-config-prod.yaml`
- Create: `services/risk-service/src/main/resources/shardingsphere-config-prod.yaml`
- Modify: `services/aggregation-service/src/main/resources/shardingsphere-config-prod.yaml`
- Modify: `.gitignore`

**Step 1: Parameterize shared runtime values**

Replace hardcoded local endpoints with env-backed defaults in the three deployed business services:

- Redis host, port, password
- Nacos server address, username, password
- RocketMQ name-server
- `database.env` default

Also parameterize:

- `short-link.domain.default` in `aggregation-service`
- `DASHSCOPE_API_KEY` in `risk-service` remains required at runtime

Use local defaults so existing developer startup still works.

**Step 2: Standardize production DB configs**

Use `database.env=prod` for production and keep `dev` for local.

Create or update `shardingsphere-config-prod.yaml` files so all deployed services use Compose service names:

- MySQL host: `mysql`
- Port: `3306`
- Database names: `link`, `link_cold`
- Username/password from the production convention used by the deployment bundle

Do not introduce a new `docker` environment. Production should be one consistent profile across services.

**Step 3: Ignore deployment secrets**

Add `docker/.env` to `.gitignore`.

**Step 4: Verify the services package**

Run:

```bash
mvn -Dmaven.repo.local=/tmp/.m2 \
  -pl services/aggregation-service,services/stats-service,services/risk-service \
  -am -DskipTests package
```

Expected:

- Build succeeds
- The packaged artifacts exist:
  - `services/aggregation-service/target/shortlink-aggregation-service.jar`
  - `services/stats-service/target/shortlink-stats-service.jar`
  - `services/risk-service/target/shortlink-risk-service.jar`

**Step 5: Commit**

```bash
git add services/aggregation-service/src/main/resources/application.yaml \
        services/stats-service/src/main/resources/application.yaml \
        services/risk-service/src/main/resources/application.yaml \
        services/aggregation-service/src/main/resources/shardingsphere-config-prod.yaml \
        services/stats-service/src/main/resources/shardingsphere-config-prod.yaml \
        services/risk-service/src/main/resources/shardingsphere-config-prod.yaml \
        .gitignore
git commit -m "feat: add production runtime config for deployed services"
```

### Task 3: Add Docker build assets for runtime images

**Files:**
- Create: `docker/Dockerfile.backend`
- Create: `docker/Dockerfile.frontend`
- Create: `docker/nginx/default.conf`

**Step 1: Add a shared backend runtime Dockerfile**

Create one backend runtime Dockerfile that:

- Uses `eclipse-temurin:17-jre-alpine`
- Accepts `ARG JAR_FILE`
- Copies the prebuilt JAR into the image
- Exposes a configurable app port via runtime config
- Uses a simple `java -jar /app/app.jar` entrypoint

This Dockerfile is reused for gateway, aggregation, stats, and risk images.

**Step 2: Add the frontend Dockerfile**

Create a frontend Dockerfile that:

- Uses `node:18-alpine` as the builder
- Runs `npm ci`
- Runs `npm run build`
- Uses `nginx:alpine` as the runtime image
- Copies `dist/` into `/usr/share/nginx/html`
- Copies the custom Nginx config into the container

**Step 3: Add the Nginx console config**

The Nginx config should:

- Serve the SPA on `/`
- Proxy `/api/` to `http://gateway:8000`
- Forward upgrade headers for websocket traffic
- Keep SPA fallback with `try_files`

Do not proxy root short-link traffic through this Nginx config in the first rollout. Redirect traffic continues to hit `aggregation-service:8003`.

**Step 4: Build the images locally**

Run:

```bash
mvn -Dmaven.repo.local=/tmp/.m2 \
  -pl services/aggregation-service,services/gateway-service,services/stats-service,services/risk-service \
  -am -DskipTests package

docker build -f docker/Dockerfile.backend \
  --build-arg JAR_FILE=services/gateway-service/target/shortlink-gateway-service.jar \
  -t shortlink/gateway:local .

docker build -f docker/Dockerfile.backend \
  --build-arg JAR_FILE=services/aggregation-service/target/shortlink-aggregation-service.jar \
  -t shortlink/aggregation:local .

docker build -f docker/Dockerfile.backend \
  --build-arg JAR_FILE=services/stats-service/target/shortlink-stats-service.jar \
  -t shortlink/stats:local .

docker build -f docker/Dockerfile.backend \
  --build-arg JAR_FILE=services/risk-service/target/shortlink-risk-service.jar \
  -t shortlink/risk:local .

docker build -f docker/Dockerfile.frontend -t shortlink/frontend:local ./console-vue
```

Expected:

- All five images build successfully

**Step 5: Commit**

```bash
git add docker/Dockerfile.backend docker/Dockerfile.frontend docker/nginx/default.conf
git commit -m "feat: add runtime Docker build assets"
```

### Task 4: Add an application Compose file and deployment env template

**Files:**
- Create: `docker/docker-compose.app.yml`
- Create: `docker/.env.example`

**Step 1: Create the application Compose file**

Define these services:

- `gateway`
- `aggregation`
- `stats`
- `risk`
- `frontend`

Rules:

- Images come from `ghcr.io/xiaoyu994/shortlink-*`
- `frontend` publishes `80:80`
- `aggregation` publishes `8003:8003`
- `gateway`, `stats`, and `risk` stay internal unless an operational port is explicitly needed
- All services read tags and runtime values from `.env`
- All services share the same Compose project and network as `docker/docker-compose.yml`

Set runtime values so the app talks to:

- `redis:6379`
- `nacos:8848`
- `namesrv:9876`
- `mysql:3306` through the production ShardingSphere files

Set `SPRING_PROFILES_ACTIVE=aggregation` for `gateway`.

**Step 2: Create the deployment env template**

Document the required variables, including:

- `GHCR_IMAGE_TAG`
- `SPRING_PROFILES_ACTIVE`
- `DATABASE_ENV`
- `SPRING_DATA_REDIS_PASSWORD`
- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`
- `SPRING_CLOUD_NACOS_DISCOVERY_USERNAME`
- `SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD`
- `ROCKETMQ_NAME_SERVER`
- `SHORT_LINK_DOMAIN_DEFAULT`
- `DASHSCOPE_API_KEY`
- `GHCR_USERNAME`
- `GHCR_TOKEN`

Add a note that `GHCR_USERNAME` and `GHCR_TOKEN` are required on the VM when GHCR packages are private.

**Step 3: Validate the Compose model**

Run:

```bash
docker compose --project-name shortlink \
  -f docker/docker-compose.yml \
  -f docker/docker-compose.app.yml \
  --env-file docker/.env.example \
  config
```

Expected:

- Compose renders successfully
- Service names resolve against one project
- No invalid references to missing networks, files, or variables remain

**Step 4: Commit**

```bash
git add docker/docker-compose.app.yml docker/.env.example
git commit -m "feat: add application compose deployment bundle"
```

### Task 5: Add server bootstrap and update scripts

**Files:**
- Create: `deploy/setup-server.sh`

**Step 1: Create the one-time setup script**

The script should:

- Install Docker and Docker Compose plugin if missing
- Create `/opt/shortlink`
- Create `/opt/shortlink/docker`, `/opt/shortlink/resources`, `/opt/shortlink/deploy`
- Expect `docker/.env` to exist before starting the stack
- Log in to GHCR with `GHCR_USERNAME` and `GHCR_TOKEN`

**Step 2: Start infrastructure and apps with one fixed project name**

Use:

```bash
docker compose --project-name shortlink -f docker/docker-compose.yml up -d
docker compose --project-name shortlink -f docker/docker-compose.yml -f docker/docker-compose.app.yml up -d
```

This avoids the broken "external network guessing" pattern that would happen if infra and apps were started as unrelated Compose projects.

**Step 3: Add basic readiness checks**

Wait for:

- MySQL health check
- Nacos reachable on `8848`
- RocketMQ namesrv reachable on `9876`

Then start the app stack and print:

- `docker compose ps`
- Frontend URL
- Aggregation redirect URL pattern

**Step 4: Dry-run the script on a clean VM**

Run:

```bash
bash deploy/setup-server.sh
```

Expected:

- The stack starts without manual Compose edits
- `docker compose ps` shows infra and application containers

**Step 5: Commit**

```bash
git add deploy/setup-server.sh
git commit -m "feat: add server bootstrap script"
```

### Task 6: Add the GitHub Actions workflow

**Files:**
- Create: `.github/workflows/deploy.yml`

**Step 1: Build and package artifacts**

The workflow should trigger on pushes to `main`.

Add a backend build job that runs:

```bash
mvn -Dmaven.repo.local=/tmp/.m2 \
  -pl services/aggregation-service,services/gateway-service,services/stats-service,services/risk-service \
  -am -DskipTests package
```

Add a frontend build job that runs:

```bash
cd console-vue
npm ci
npm run build
```

**Step 2: Build and push images to GHCR**

Use `docker/login-action` with `GITHUB_TOKEN` for pushes from Actions.

Build and push:

- `ghcr.io/xiaoyu994/shortlink-gateway-service`
- `ghcr.io/xiaoyu994/shortlink-aggregation-service`
- `ghcr.io/xiaoyu994/shortlink-stats-service`
- `ghcr.io/xiaoyu994/shortlink-risk-service`
- `ghcr.io/xiaoyu994/shortlink-frontend`

Tag each image with:

- `latest`
- `${{ github.sha }}`

**Step 3: Ship deployment assets to the VM**

Upload these paths to `/opt/shortlink` on the VM:

- `docker/`
- `resources/database/link.sql`
- `deploy/setup-server.sh`

This is required because the existing infrastructure compose file mounts local monitoring config, MySQL init scripts, and SQL bootstrap files.

**Step 4: Deploy over SSH**

Use `appleboy/ssh-action` to:

- Log in to GHCR on the VM
- Write or update `docker/.env`
- Pull the new app images
- Run:

```bash
docker compose --project-name shortlink -f docker/docker-compose.yml up -d
docker compose --project-name shortlink -f docker/docker-compose.yml -f docker/docker-compose.app.yml pull
docker compose --project-name shortlink -f docker/docker-compose.yml -f docker/docker-compose.app.yml up -d
docker compose --project-name shortlink -f docker/docker-compose.yml -f docker/docker-compose.app.yml ps
```

**Step 5: Define required GitHub Secrets**

Document these repository secrets:

- `SERVER_HOST`
- `SERVER_USER`
- `SERVER_SSH_KEY`
- `GHCR_USERNAME`
- `GHCR_TOKEN`
- `DASHSCOPE_API_KEY`
- `SHORT_LINK_DOMAIN_DEFAULT`

Optional, if production enables Nacos auth:

- `NACOS_USERNAME`
- `NACOS_PASSWORD`

**Step 6: Verify workflow syntax and a full dry run**

Run locally if available:

```bash
yamllint .github/workflows/deploy.yml
```

Then push a branch and validate in GitHub Actions before merging to `main`.

Expected:

- Images appear in GHCR
- Deploy job finishes without SSH or Compose path failures

**Step 7: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "feat: add automated deployment workflow"
```

### Task 7: Perform end-to-end deployment verification

**Files:**
- No new files required

**Step 1: Verify the local container stack**

Run:

```bash
docker compose --project-name shortlink \
  -f docker/docker-compose.yml \
  -f docker/docker-compose.app.yml \
  --env-file docker/.env.example \
  up -d
```

Expected checks:

- Console loads on `http://localhost`
- Username check works through Nginx and gateway:

```bash
curl "http://localhost/api/short-link/admin/v1/user/has-username?username=test"
```

- Aggregation health works:

```bash
curl http://localhost:8003/actuator/health
```

- A created short link redirects through port `8003`

**Step 2: Verify the VM after a real `main` deploy**

Run on the VM:

```bash
cd /opt/shortlink
docker compose --project-name shortlink -f docker/docker-compose.yml -f docker/docker-compose.app.yml ps
curl http://127.0.0.1
curl http://127.0.0.1:8003/actuator/health
```

Also verify from the browser:

- Console login
- Short-link create/update flows
- Stats pages still load
- Risk-control path still emits and consumes MQ events

**Step 3: Add rollback notes before declaring done**

Rollback procedure:

- Re-run deploy with the previous image tag in `docker/.env`
- `docker compose ... up -d`

Do not declare the rollout complete until rollback was tested at least once with a previous SHA tag.

**Step 4: Commit**

```bash
git commit --allow-empty -m "chore: verify automated deployment rollout"
```

## Definition of Done

- Pushing to `main` builds all required runtime images and pushes them to GHCR
- The VM can pull those images without manual intervention
- The deployed topology includes `frontend`, `gateway`, `aggregation`, `stats`, and `risk`
- Console APIs work through Nginx and gateway
- Short-link redirects still work through `aggregation-service:8003`
- Stats pages still function after deployment
- Risk-control consumers still process MQ traffic after deployment
- Rollback using a previous image tag is documented and tested
