import http from '../axios'

export default {
  queryNotifications(params) {
    return http({
      url: '/notification',
      method: 'get',
      params
    })
  },
  queryUnreadCount() {
    return http({
      url: '/notification/unread-count',
      method: 'get'
    })
  },
  markRead(data) {
    return http({
      url: '/notification/read',
      method: 'put',
      data
    })
  },
  markAllRead() {
    return http({
      url: '/notification/read-all',
      method: 'put'
    })
  }
}
