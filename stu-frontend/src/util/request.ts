import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

export type ApiEnvelope<T> = { code: 202 | 505; message: string; data: T }
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090',
})
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('stu_manage_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response
    const body = response.data as ApiEnvelope<unknown>
    if (body?.code === 202) return response
    const message = body?.message || '请求未能完成，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error) => {
    const status = error.response?.status
    const isLoginRequest = error.config?.url === '/api/auth/login'
    const message =
      status === 401
        ? isLoginRequest
          ? '账号、密码或登录身份不正确'
          : '登录已失效，请重新登录'
        : status === 403
          ? '没有权限执行此操作'
          : error.response?.data?.message || '网络连接异常，请稍后重试'
    if (status === 401 && !isLoginRequest) {
      localStorage.removeItem('stu_manage_token')
      localStorage.removeItem('stu_manage_user')
      if (!window.location.pathname.startsWith('/login')) {
        const redirect = encodeURIComponent(window.location.pathname)
        window.location.replace(`/login?redirect=${redirect}`)
      }
    }
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
)
export async function api<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await request.request<ApiEnvelope<T>>(config)
  return response.data.data
}
export async function download(url: string, filename: string) {
  const response = await request.get(url, { responseType: 'blob' })
  const blob = new Blob([response.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

/** Convert backend static paths such as /uploads/demo.mp4 into browser URLs. */
export function assetUrl(url: string) {
  if (/^https?:\/\//i.test(url)) return url
  return `${request.defaults.baseURL}${url.startsWith('/') ? url : `/${url}`}`
}
export default request
