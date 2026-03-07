# 短链接封禁通知铃铛交互设计

## 1. 背景

当前风控链路已经会在短链接被判定违规并封禁后，向 `t_user_notification` 写入一条站内信通知。但用户侧无法查看这些通知：

- 前端没有通知入口与展示界面
- 后端没有供用户查询、标记已读、获取未读数的接口
- 封禁通知无法实时反馈给正在使用控制台的用户

目标是在现有控制台顶部补一个“铃铛 + 下拉通知面板”，让用户无需跳转页面即可查看封禁通知，并支持实时更新。

## 2. 目标

实现一个最小但完整的通知闭环：

1. 用户能在控制台顶栏看到通知铃铛与未读角标
2. 点击铃铛后出现下拉通知面板，而不是跳转独立页面
3. 用户可查看通知列表、筛选未读、单条已读、全部已读
4. 当新的封禁通知产生时，前端能通过 WebSocket 实时感知并刷新
5. HTTP 接口作为初始化和断线兜底渠道，保证可用性

## 3. 方案选择

### 方案 A：纯 HTTP 轮询
- 优点：实现简单
- 缺点：实时性不足，用户体验较弱

### 方案 B：HTTP + WebSocket
- 优点：初始化和兜底走 HTTP，实时更新走 WebSocket，用户体验最好
- 缺点：需要维护连接和重连逻辑

### 方案 C：HTTP + SSE
- 优点：比 WebSocket 更轻量
- 缺点：在当前项目结构下没有明显优势，前端和后端都没有现成模式可复用

### 推荐方案
采用 **方案 B：HTTP + WebSocket**。

原因：
- 最符合“通知铃铛”的产品体验
- 不影响现有用户后台接口结构
- 即便实时通道断开，HTTP 仍可保证通知可见

## 4. 架构设计

### 4.1 服务职责划分

#### `risk-service`
负责：
- 判定短链接违规
- 落库一条 `t_user_notification` 通知记录
- 在通知落库成功后，发布“新通知到达事件”

#### `user-service`
负责：
- 提供通知查询接口
- 提供未读数接口
- 提供单条/全部已读接口
- 提供 WebSocket 实时推送能力
- 消费“新通知到达事件”，将通知推送给在线用户

#### `console-vue`
负责：
- 顶部铃铛 UI
- 下拉通知面板
- HTTP 初始化数据拉取
- WebSocket 实时通知接收与重连

### 4.2 为什么通知读取不放到 `risk-service`

虽然通知由 `risk-service` 生成，但用户后台入口和网关管理路径都更适合由 `user-service` 暴露：

- 网关已有 `/api/short-link/admin/** -> user-service` 路由习惯
- 前端用户中心相关接口本来就走 `user-service`
- `t_user_notification` 与用户使用场景天然更贴近 `user-service`
- 避免为了一个读取接口再扩一条额外 admin 路由到 `risk-service`

## 5. 后端接口设计

基路径：`/api/short-link/admin/v1/notification`

### 5.1 查询通知列表
`GET /api/short-link/admin/v1/notification`

参数：
- `current`
- `size`
- `readFlag` 可选

返回字段：
- `id`
- `type`
- `title`
- `content`
- `readFlag`
- `createTime`

默认规则：
- 仅查询当前登录用户
- 按 `create_time desc` 倒序

### 5.2 查询未读数
`GET /api/short-link/admin/v1/notification/unread-count`

返回：
- `count`

### 5.3 单条已读
`PUT /api/short-link/admin/v1/notification/read`

参数：
- `id`

规则：
- 只允许操作当前登录用户自己的通知

### 5.4 全部已读
`PUT /api/short-link/admin/v1/notification/read-all`

规则：
- 将当前登录用户的未读通知批量设为已读

## 6. WebSocket 设计

### 6.1 入口
建议在 `user-service` 提供：
- `/ws/notification`

### 6.2 认证
复用当前登录 token。
连接建立时携带 token，服务端解析用户身份并绑定用户会话。

### 6.3 推送内容
推送“精简通知 DTO”：
- `id`
- `type`
- `title`
- `content`
- `readFlag`
- `createTime`

这样前端收到后可以直接插入当前列表顶部，而不是再额外发一次查询请求。

### 6.4 事件来源
当 `risk-service` 的违规通知消费者落库成功后，再发布一个“用户通知创建事件”，由 `user-service` 消费并推送给在线用户。

## 7. 前端交互设计

### 7.1 顶部入口
位置：`console-vue/src/views/home/HomeIndex.vue`

表现：
- 顶栏用户名旁边增加一个铃铛图标按钮
- 外层包 `el-badge` 显示未读数
- 点击后弹出 `el-popover`

### 7.2 下拉通知面板
建议独立组件：
- `console-vue/src/components/NotificationBell.vue`

包含：
- 标题：`通知`
- 筛选：`全部` / `未读`
- 操作：`全部已读`
- 列表项：
  - 标题
  - 内容
  - 时间
  - 已读/未读状态
- 空状态：`暂无通知`

### 7.3 用户操作
- 点击单条通知：标记已读
- 点击“全部已读”：批量已读
- 面板打开时收到新通知：插入顶部
- 面板关闭时收到新通知：只刷新 badge 数，并在下次打开时显示

## 8. 前端技术实现建议

现有 `console-vue` 已使用 Element Plus，可直接复用：
- `el-badge`
- `el-popover`
- `el-scrollbar`
- `el-empty`
- `el-button`
- `el-tag`

前端新增：
- `console-vue/src/api/modules/notification.js`
- `console-vue/src/components/NotificationBell.vue`

## 9. 数据与表结构判断

当前 `t_user_notification` 表字段如下：
- `id`
- `user_id`
- `type`
- `title`
- `content`
- `read_flag`
- `create_time`

该结构已满足第一版需求，**无需新增表字段**，也不需要引入富文本。

## 10. 第一版明确包含的能力

本次实现第一版直接包含：
- 铃铛按钮
- 下拉通知面板
- 未读数
- 单条已读
- 全部已读
- WebSocket 实时通知
- HTTP 初始化和兜底

## 11. 第一版暂不处理

以下能力不纳入本轮：
- 删除通知
- 多通知类型分类体系扩展
- 通知与业务详情深度联动跳转
- 历史通知无限滚动

## 12. 验收标准

实现完成后，应满足：

1. 风控链路新增一条封禁通知后，用户顶栏铃铛未读数增加
2. 用户点击铃铛，可看到新通知内容
3. 用户可单条已读、全部已读
4. 页面刷新后通知仍可通过 HTTP 接口恢复
5. WebSocket 断开后，HTTP 查询仍可正常使用
