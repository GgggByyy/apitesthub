import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from './router'

// axios 统一封装：自动带 token、统一错误提示、401 踢回登录页
const http = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 15000
})

http.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  resp => {
    const body = resp.data
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body.data
  },
  err => {
    const body = err.response?.data
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
      ElMessage.error('登录已失效，请重新登录')
    } else {
      ElMessage.error(body?.message || err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default http
