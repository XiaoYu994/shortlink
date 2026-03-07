<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-end"
    :width="380"
    trigger="click"
    popper-class="notification-bell-popper"
    @show="handleShow"
  >
    <template #reference>
      <el-badge :hidden="unreadCount <= 0" :value="badgeValue" :max="99" class="notification-bell-badge">
        <el-button class="notification-bell-trigger" text>
          <el-icon :size="18"><Bell /></el-icon>
        </el-button>
      </el-badge>
    </template>

    <div class="notification-panel">
      <div class="notification-header">
        <span class="notification-title">通知</span>
        <el-button link type="primary" :disabled="unreadCount === 0 || allReadLoading" @click="handleMarkAllRead">
          全部已读
        </el-button>
      </div>

      <div class="notification-filter">
        <el-button :type="activeFilter === 'all' ? 'primary' : 'default'" size="small" plain @click="changeFilter('all')">
          全部
        </el-button>
        <el-button :type="activeFilter === 'unread' ? 'primary' : 'default'" size="small" plain @click="changeFilter('unread')">
          未读
        </el-button>
      </div>

      <el-scrollbar max-height="420px">
        <div v-if="loading" class="notification-loading">
          <el-skeleton :rows="3" animated />
        </div>
        <el-empty v-else-if="notifications.length === 0" description="暂无通知" :image-size="72" />
        <div v-else class="notification-list">
          <div
            v-for="item in notifications"
            :key="item.id"
            class="notification-item"
            :class="{ unread: item.readFlag === 0 }"
          >
            <div class="notification-item-header">
              <div class="notification-item-title-wrap">
                <span class="notification-item-title">{{ item.title }}</span>
                <el-tag v-if="item.readFlag === 0" size="small" type="danger" effect="plain">未读</el-tag>
                <el-tag v-else size="small" effect="plain">已读</el-tag>
              </div>
              <el-button
                v-if="item.readFlag === 0"
                link
                type="primary"
                class="notification-read-btn"
                @click="handleMarkRead(item)"
              >
                标记已读
              </el-button>
            </div>
            <div class="notification-item-content">{{ item.content }}</div>
            <div class="notification-item-time">{{ formatTime(item.createTime) }}</div>
          </div>
        </div>
      </el-scrollbar>
    </div>
  </el-popover>
</template>

<script setup>
import { Bell } from '@element-plus/icons-vue'
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getToken } from '@/core/auth'

const { proxy } = getCurrentInstance()
const API = proxy.$API

const visible = ref(false)
const loading = ref(false)
const allReadLoading = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
const activeFilter = ref('all')
const pageParams = ref({
  current: 1,
  size: 20
})

let socket = null
let reconnectTimer = null
let reconnectAttempts = 0
let manualClose = false

const badgeValue = computed(() => unreadCount.value)

function currentReadFlag() {
  return activeFilter.value === 'unread' ? 0 : undefined
}

async function fetchUnreadCount() {
  const res = await API.notification.queryUnreadCount()
  unreadCount.value = Number(res.data ?? 0)
}

async function fetchNotifications() {
  loading.value = true
  try {
    const res = await API.notification.queryNotifications({
      ...pageParams.value,
      readFlag: currentReadFlag()
    })
    notifications.value = res.data?.records ?? []
  } finally {
    loading.value = false
  }
}

async function handleShow() {
  await Promise.all([fetchUnreadCount(), fetchNotifications()])
}

async function changeFilter(filter) {
  activeFilter.value = filter
  await fetchNotifications()
}

async function handleMarkRead(item) {
  await API.notification.markRead({ id: item.id })
  item.readFlag = 1
  unreadCount.value = Math.max(0, unreadCount.value - 1)
  if (activeFilter.value === 'unread') {
    notifications.value = notifications.value.filter(each => each.id !== item.id)
  }
}

async function handleMarkAllRead() {
  allReadLoading.value = true
  try {
    await API.notification.markAllRead()
    unreadCount.value = 0
    notifications.value = notifications.value.map(item => ({ ...item, readFlag: 1 }))
    if (activeFilter.value === 'unread') {
      notifications.value = []
    }
  } finally {
    allReadLoading.value = false
  }
}

function wsBaseUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/api/short-link/admin/v1/notification/ws`
}

function connectSocket() {
  const token = getToken()
  if (!token) return
  manualClose = false
  const url = `${wsBaseUrl()}?token=${encodeURIComponent(token)}`
  socket = new WebSocket(url)

  socket.onopen = () => {
    reconnectAttempts = 0
  }

  socket.onmessage = async (event) => {
    try {
      const payload = JSON.parse(event.data)
      const normalized = {
        ...payload,
        readFlag: payload.readFlag ?? 0
      }
      unreadCount.value = unreadCount.value + (normalized.readFlag === 0 ? 1 : 0)
      if (activeFilter.value === 'all' || normalized.readFlag === 0) {
        notifications.value = [normalized, ...notifications.value.filter(item => item.id !== normalized.id)]
      }
    } catch (err) {
      console.error('notification websocket parse failed', err)
      await Promise.allSettled([fetchUnreadCount(), visible.value ? fetchNotifications() : Promise.resolve()])
    }
  }

  socket.onclose = () => {
    socket = null
    if (!manualClose) {
      scheduleReconnect()
    }
  }

  socket.onerror = () => {
    if (socket) {
      socket.close()
    }
  }
}

function scheduleReconnect() {
  clearReconnectTimer()
  const delay = Math.min(10000, 1000 * Math.max(1, reconnectAttempts + 1))
  reconnectAttempts += 1
  reconnectTimer = window.setTimeout(() => {
    connectSocket()
  }, delay)
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function closeSocket() {
  manualClose = true
  clearReconnectTimer()
  if (socket) {
    socket.close()
    socket = null
  }
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

onMounted(async () => {
  try {
    await fetchUnreadCount()
  } catch (err) {
    console.warn('通知未读数加载失败', err)
  }
  connectSocket()
})

onBeforeUnmount(() => {
  closeSocket()
})
</script>

<style scoped lang="scss">
.notification-bell-badge {
  margin-right: 16px;
}

.notification-bell-trigger {
  color: #fff;
  opacity: 0.75;
  padding: 6px 8px;
}

.notification-bell-trigger:hover {
  color: #fff;
  opacity: 1;
}

.notification-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notification-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.notification-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.notification-filter {
  display: flex;
  gap: 8px;
}

.notification-loading {
  padding: 8px 4px;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 6px;
}

.notification-item {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px;
  transition: all 0.2s ease;
  background-color: #fff;
}

.notification-item.unread {
  background-color: #f8fbff;
  border-color: #d9ecff;
}

.notification-item-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.notification-item-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notification-item-title {
  font-size: 13px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.notification-read-btn {
  padding: 0;
  height: auto;
}

.notification-item-content {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: rgba(0, 0, 0, 0.65);
  white-space: pre-wrap;
  word-break: break-word;
}

.notification-item-time {
  margin-top: 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
</style>
