import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

export type ApiEnvelope<T> = { code: 202 | 505; message: string; data: T }
const request = axios.create({
  // 生产环境由 Spring Boot 同源提供页面和接口；本地开发时再交给 Vite 代理。
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
})
request.interceptors.request.use((config) => {
  // 所有 Axios 业务请求统一补上令牌，页面不需要各自处理鉴权头。
  const token = localStorage.getItem('stu_manage_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
request.interceptors.response.use(
  (response) => {
    // 下载响应是二进制，不能按业务信封 code 解包；JSON 失败仍由下面分支提示。
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
  // 后端成功响应统一为 { code: 202, message, data }，组件只取得业务 data。
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

/** 将后端返回的 /uploads/demo.mp4 等静态路径转换为当前部署地址。 */
export function assetUrl(url: string) {
  if (/^https?:\/\//i.test(url)) return url
  return `${request.defaults.baseURL || ''}${url.startsWith('/') ? url : `/${url}`}`
}

/** SSE 等原生 fetch 请求也使用同一地址，单端口部署和本地代理都可用。 */
export function apiUrl(path: string) {
  return `${request.defaults.baseURL || ''}${path.startsWith('/') ? path : `/${path}`}`
}
export default request
