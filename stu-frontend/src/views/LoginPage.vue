<template>
  <div class="loginPage">
    <el-form label-width="auto" class="loginForm">
      <el-form-item label="username">
        <el-input v-model="loginForm.username"></el-input>
      </el-form-item>
      <el-form-item label="password">
        <el-input v-model="loginForm.password"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="login" style="width: 100%">登录</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import request from '@/util/request.ts'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loginForm = ref({
  username: 'zhangsan',
  password: '',
})
const login = async () => {
  const res = await request({
    method: 'POST',
    url: '/api/login/manger',
    data:loginForm.value
  })
  console.log(res)
  localStorage.setItem("token",res.data)
  if(res.data.code===505){
    ElMessage.warning("后端业务异常"+res.data.message);
    return
  }
  router.push('/student')
}
</script>
/*如果父标签有一个元素可以居中 */
<style scoped>
.loginPage {
  background-color: brown;
  height: 100vh;

  display: flex;
  justify-content: center;
  align-items: center;
}
.loginForm {
  width: 400px;
  background-color: #42b983;
  padding: 20px;
}
</style>
