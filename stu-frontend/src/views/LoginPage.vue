<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { FormInstance, FormRules } from 'element-plus'
import type { Role } from '@/types'
import { School } from '@element-plus/icons-vue'
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '', role: 'STUDENT' as Role })
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login(form.username, form.password, form.role)
    router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <main class="login">
    <section class="intro">
      <div class="intro-brand">
        <el-icon><School /></el-icon> 知行教务
      </div>
      <div>
        <p>校园学习与教学管理平台</p>
        <h1>让每一次教学安排<br />都有清晰的回应。</h1>
      </div>
      <footer>学生选课、课程成绩、教学事务，集中在一个值得信赖的工作空间。</footer>
    </section>
    <section class="login-panel">
      <div class="form-wrap">
        <p class="eyebrow">欢迎回来</p>
        <h2>登录系统</h2>
        <p class="hint">请选择身份后输入账号和密码</p>
        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="submit"
          ><el-form-item prop="role"
            ><el-radio-group v-model="form.role" class="role-select"
              ><el-radio-button label="STUDENT">学生</el-radio-button
              ><el-radio-button label="TEACHER">教师</el-radio-button
              ><el-radio-button label="ADMIN">教学管理</el-radio-button></el-radio-group
            ></el-form-item
          ><el-form-item prop="username"
            ><el-input
              v-model="form.username"
              size="large"
              placeholder="账号或学号"
              autocomplete="username" /></el-form-item
          ><el-form-item prop="password"
            ><el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="密码"
              autocomplete="current-password"
              @keyup.enter="submit" /></el-form-item
          ><el-button
            type="primary"
            size="large"
            native-type="submit"
            :loading="loading"
            class="login-button"
            >登录</el-button
          ></el-form
        >
      </div>
    </section>
  </main>
</template>
<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
  background: #fff;
}
.intro {
  min-height: 100vh;
  padding: 48px clamp(38px, 8vw, 130px);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: #fff;
  background: #123d2d;
}
.intro-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 700;
  font-size: 18px;
}
.intro-brand .el-icon {
  padding: 7px;
  border-radius: 8px;
  background: #d8f0e4;
  color: #176346;
}
.intro p {
  margin: 0 0 17px;
  color: #b9d7c8;
  font-size: 16px;
}
.intro h1 {
  margin: 0;
  font-size: clamp(34px, 4vw, 58px);
  line-height: 1.24;
  letter-spacing: -1.5px;
}
.intro footer {
  max-width: 340px;
  color: #b9d7c8;
  font-size: 13px;
  line-height: 1.8;
}
.login-panel {
  display: grid;
  place-items: center;
  padding: 32px;
}
.form-wrap {
  width: min(380px, 100%);
}
.eyebrow {
  margin: 0 0 9px;
  color: #176346;
  font-size: 13px;
  font-weight: 700;
}
.form-wrap h2 {
  margin: 0 0 8px;
  font-size: 30px;
}
.hint {
  margin: 0 0 28px;
  color: var(--muted);
}
.role-select {
  display: flex;
  width: 100%;
}
.role-select :deep(.el-radio-button) {
  flex: 1;
}
.role-select :deep(.el-radio-button__inner) {
  width: 100%;
  padding-inline: 4px;
}
.login-button {
  width: 100%;
  margin-top: 5px;
}
@media (max-width: 760px) {
  .login {
    display: block;
  }
  .intro {
    min-height: 260px;
    padding: 28px 26px;
  }
  .intro footer {
    display: none;
  }
  .intro h1 {
    font-size: 30px;
  }
  .login-panel {
    padding: 44px 25px;
  }
}
</style>
