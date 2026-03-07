# Notification Bell Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a bell-style notification dropdown for banned short-link messages with HTTP query/read APIs and WebSocket real-time updates.

**Architecture:** `risk-service` continues to generate violation notifications, then emits a lightweight "notification created" event. `user-service` becomes the user-facing notification read/push boundary: it serves list/unread/read APIs and pushes real-time notifications over WebSocket. `console-vue` adds a top-bar bell component that loads notifications via HTTP and receives new ones via WebSocket.

**Tech Stack:** Spring Boot MVC, RocketMQ, MyBatis-Plus, Vue 3, Element Plus, native WebSocket.

---

### Task 1: Add user-service notification persistence layer

**Files:**
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/dao/entity/UserNotificationDO.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/dao/mapper/UserNotificationMapper.java`
- Test: `services/user-service/src/test/java/com/xhy/shortlink/biz/userservice/service/UserNotificationServiceTest.java`

**Step 1: Write the failing test**
- Add a focused test that proves user notification records can be queried by current user and read flag.

**Step 2: Run test to verify it fails**
Run: `mvn -pl services/user-service -Dtest=UserNotificationServiceTest test`
Expected: fail because entity/mapper/service do not exist.

**Step 3: Write minimal implementation**
- Add `UserNotificationDO` mapped to `t_user_notification`
- Add `UserNotificationMapper`

**Step 4: Run test to verify it passes**
Run: same command
Expected: pass or move to next missing piece.

**Step 5: Commit**
- Commit after service layer is complete, not yet in this task.

### Task 2: Add user-service notification query/read service

**Files:**
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/dto/req/NotificationPageReqDTO.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/dto/req/NotificationReadReqDTO.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/dto/resp/UserNotificationRespDTO.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/dto/resp/UserNotificationUnreadCountRespDTO.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/service/UserNotificationService.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/service/impl/UserNotificationServiceImpl.java`
- Test: `services/user-service/src/test/java/com/xhy/shortlink/biz/userservice/service/UserNotificationServiceTest.java`

**Step 1: Write the failing test**
- Add tests for:
  - paged query by current user
  - unread count
  - mark single read
  - mark all read

**Step 2: Run test to verify it fails**
Run: `mvn -pl services/user-service -Dtest=UserNotificationServiceTest test`
Expected: fail on missing service methods.

**Step 3: Write minimal implementation**
- Read current user from `UserContext`
- Query notifications ordered by `createTime desc`
- Return unread count
- Update `readFlag`

**Step 4: Run test to verify it passes**
Run: same command
Expected: pass.

### Task 3: Expose user-service notification HTTP APIs

**Files:**
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/controller/UserNotificationController.java`
- Test: `services/user-service/src/test/java/com/xhy/shortlink/biz/userservice/controller/UserNotificationControllerTest.java`

**Step 1: Write the failing test**
- Add controller tests for:
  - `GET /api/short-link/admin/v1/notification`
  - `GET /api/short-link/admin/v1/notification/unread-count`
  - `PUT /api/short-link/admin/v1/notification/read`
  - `PUT /api/short-link/admin/v1/notification/read-all`

**Step 2: Run test to verify it fails**
Run: `mvn -pl services/user-service -Dtest=UserNotificationControllerTest test`
Expected: fail because controller is missing.

**Step 3: Write minimal implementation**
- Delegate to `UserNotificationService`
- Reuse existing `Result/Results` response style

**Step 4: Run test to verify it passes**
Run: same command
Expected: pass.

### Task 4: Add notification-created event contract in risk-service

**Files:**
- Create: `services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/mq/event/UserNotificationCreatedEvent.java`
- Create: `services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/mq/producer/UserNotificationCreatedProducer.java`
- Modify: `services/risk-service/src/main/java/com/xhy/shortlink/biz/riskservice/common/constant/RocketMQConstant.java`
- Test: `services/risk-service/src/test/java/com/xhy/shortlink/biz/riskservice/mq/consumer/ShortLinkViolationNotifyConsumerTest.java`

**Step 1: Write the failing test**
- Add a test proving violation notification generation also emits a notification-created event after DB insert.

**Step 2: Run test to verify it fails**
Run: `mvn -pl services/risk-service -Dtest=ShortLinkViolationNotifyConsumerTest test`
Expected: fail due to missing producer/event.

**Step 3: Write minimal implementation**
- Add event DTO and producer
- Update violation notify consumer to publish after successful insert or duplicate-safe path decision

**Step 4: Run test to verify it passes**
Run: same command
Expected: pass.

### Task 5: Add user-service WebSocket push endpoint

**Files:**
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/config/NotificationWebSocketConfig.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/websocket/NotificationWebSocketHandler.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/websocket/NotificationSessionManager.java`
- Test: `services/user-service/src/test/java/com/xhy/shortlink/biz/userservice/websocket/NotificationWebSocketHandlerTest.java`

**Step 1: Write the failing test**
- Add tests covering:
  - handshake/auth token extraction
  - user session bind/unbind
  - push payload delivery to online user

**Step 2: Run test to verify it fails**
Run: `mvn -pl services/user-service -Dtest=NotificationWebSocketHandlerTest test`
Expected: fail because config/handler do not exist.

**Step 3: Write minimal implementation**
- Register `/ws/notification`
- Resolve current user from token
- Store sessions by `userId`
- Provide push method for DTO payloads

**Step 4: Run test to verify it passes**
Run: same command
Expected: pass.

### Task 6: Add user-service consumer for notification-created events

**Files:**
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/mq/event/UserNotificationCreatedEvent.java`
- Create: `services/user-service/src/main/java/com/xhy/shortlink/biz/userservice/mq/consumer/UserNotificationCreatedConsumer.java`
- Modify: `services/user-service/pom.xml` if RocketMQ dependency is missing
- Test: `services/user-service/src/test/java/com/xhy/shortlink/biz/userservice/mq/consumer/UserNotificationCreatedConsumerTest.java`

**Step 1: Write the failing test**
- Add a test showing consumer receives event and pushes message to online user sessions.

**Step 2: Run test to verify it fails**
Run: `mvn -pl services/user-service -Dtest=UserNotificationCreatedConsumerTest test`
Expected: fail because consumer is missing.

**Step 3: Write minimal implementation**
- Add event DTO mirroring risk-service producer payload
- Consume MQ event
- Push notification DTO to connected user via session manager

**Step 4: Run test to verify it passes**
Run: same command
Expected: pass.

### Task 7: Add console notification API module

**Files:**
- Create: `console-vue/src/api/modules/notification.js`
- Test: reuse existing frontend test strategy if project already has one; otherwise no new framework setup

**Step 1: Write the failing test**
- If frontend tests exist for API modules, add one for notification endpoints.
- If not, skip adding framework and keep this covered in integration/manual validation.

**Step 2: Implement minimal API wrappers**
- `queryNotifications`
- `queryUnreadCount`
- `markRead`
- `markAllRead`

**Step 3: Verify import wiring**
- Ensure `console-vue/src/api/index.js` auto-loads module.

### Task 8: Build bell dropdown component

**Files:**
- Create: `console-vue/src/components/NotificationBell.vue`
- Modify: `console-vue/src/views/home/HomeIndex.vue`
- Test: `console-vue/src/components/NotificationBell.test.*` only if test stack exists nearby

**Step 1: Write the failing UI test**
- If component tests exist, add one for:
  - unread badge display
  - popover open
  - rendering notification items
  - mark-all-read action

**Step 2: Run test to verify it fails**
- Use existing frontend test command if available.

**Step 3: Write minimal implementation**
- Add bell icon in header
- Show unread badge
- Use `el-popover` for dropdown panel
- Use `el-scrollbar` for list
- Add “全部/未读” filter and “全部已读” action

**Step 4: Run test to verify it passes**
- Use existing frontend test command if available.

### Task 9: Add frontend WebSocket integration

**Files:**
- Create: `console-vue/src/utils/notificationSocket.js` or equivalent composable/helper
- Modify: `console-vue/src/components/NotificationBell.vue`

**Step 1: Write failing test or manual acceptance note**
- If no websocket component test pattern exists, record this as manual validation task.

**Step 2: Implement minimal WebSocket client**
- Connect to `/ws/notification`
- Carry token
- Reconnect on close
- On message:
  - prepend item if panel open
  - always refresh unread count or increment locally

**Step 3: Verify behavior**
- Manual or automated depending on available test setup.

### Task 10: End-to-end validation and docs

**Files:**
- Modify: `docs/refactor/phase7-acceptance-report-2026-03-06.md` only if notification feature becomes part of acceptance evidence
- Optionally create: `docs/plans/2026-03-07-notification-bell-test-notes.md`

**Step 1: Validate HTTP APIs**
Run targeted backend tests for user/risk services.

**Step 2: Validate real-time push**
- Trigger risk ban event
- Confirm bell unread count changes without refresh
- Confirm dropdown shows notification

**Step 3: Validate read actions**
- Single read updates badge/list state
- All read clears unread count

**Step 4: Commit**
Suggested commit flow:
- `feat(user-service): add notification query and read APIs`
- `feat(user-service): add websocket notification push`
- `feat(risk-service): publish notification created event`
- `feat(console): add notification bell dropdown`
- `test(notification): add notification integration coverage`

