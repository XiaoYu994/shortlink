import axios from 'axios'
import {getToken, getUsername} from '@/core/auth.js'
import {isNotEmpty} from '@/utils/plugins.js'
import router from "@/router";
import {ElMessage} from 'element-plus'

const baseURL = '/api/short-link/admin/v1'

const http = axios.create({
    baseURL: baseURL,
    timeout: 15000
})

// 请求拦截
http.interceptors.request.use(
    (config) => {
        const token = isNotEmpty(getToken()) ? getToken() : ''
        config.headers.Token = token
        config.headers['short-link'] = token
        config.headers.Username = isNotEmpty(getUsername()) ? getUsername() : ''
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// 响应拦截
http.interceptors.response.use(
    (res) => {
        if (res.status === 200) {
            // 🔥 如果是文件流，直接返回，不校验 code
            if (res.request.responseType === 'blob' || res.headers['content-type'].includes('application/vnd.ms-excel')) {
                return res // 注意：这里返回完整 res，因为后面要从 headers 拿文件名
            }
            // 获取后端返回的 JSON 数据
            const data = res.data

            // 判断业务状态码
            if (data.code === '0') {
                // 🔥 优化1：直接返回 data，帮组件“剥”掉 Axios 的外壳
                // 组件里可以直接用 res.data 拿到数据，不用 res.data.data
                return data
            } else {
                // 业务失败
                ElMessage.error(data.message || '系统繁忙，请稍后再试')
                // 返回 reject，中断组件的 .then()
                return Promise.reject(new Error(data.message || 'Error'))
            }
        }
        return Promise.reject(res)
    },
    (err) => {
        // 🔥 优化2：健壮性处理。如果断网，err.response 是 undefined
        if (err.response && err.response.status === 401) {
            localStorage.removeItem('token')
            router.push('/login')
        }

        // 🔥 优化3：处理网络超时等没有 response 的情况
        // err.response?.data?.message 是后端返回的报错
        // err.message 是 Axios 自身的报错（如 "Network Error"）
        const msg = err.response?.data?.message || err.message || '请求失败，请检查网络'

        ElMessage.error(msg)
        return Promise.reject(err)
    }
)

export default http