import axios from 'axios'
import { ElMessage } from 'element-plus'
const instance=axios.create({
  baseURL:"http://localhost:9090"
})
//携带token
instance.interceptors.request.use((config)=>{
  const token = localStorage.getItem("token")
  if(token){
    config.headers.Authorization = token;
  }
  return config
})
// axios 拦截器 处理请求token 与返回值
instance.interceptors.response.use(
  (res)=>{
    if (res.data.code === 202) {
      // 拆一层包裹
      return res.data
    }
    if(res.data.code === 505){
      ElMessage.warning("业务异常:"+res.data.message)
      return Promise.reject(new Error(res.data.message));
    }
    return res;
  },
  (err)=>{
    return Promise.reject(err)
  })
export default instance;