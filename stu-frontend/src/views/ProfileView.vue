<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { api } from '@/util/request'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore(),
  loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
async function save() {
  if (!form.oldPassword || !form.newPassword) {
    ElMessage.warning('请填写当前密码和新密码')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await api({
      url: '/api/auth/password',
      method: 'PUT',
      data: { oldPassword: form.oldPassword, newPassword: form.newPassword },
    })
    Object.assign(form, { oldPassword: '', newPassword: '', confirmPassword: '' })
    ElMessage.success('密码已更新，请妥善保管')
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <div class="page">
    <PageHeader title="个人设置" description="查看当前账号信息并更新登录密码" />
    <div class="profile-grid">
      <section class="surface section-card">
        <h2>账号信息</h2>
        <div class="identity">
          <el-avatar :size="58" :src="auth.user?.photo">{{
            auth.user?.name?.slice(0, 1)
          }}</el-avatar>
          <div>
            <strong>{{ auth.user?.name }}</strong>
            <p>{{ auth.user?.username }}</p>
          </div>
        </div>
        <el-descriptions :column="1" border
          ><el-descriptions-item label="角色">{{
            auth.user?.role === 'STUDENT'
              ? '学生'
              : auth.user?.role === 'TEACHER'
                ? '教师'
                : '教学管理'
          }}</el-descriptions-item
          ><el-descriptions-item label="院系/部门">{{
            auth.user?.department || '未填写'
          }}</el-descriptions-item
          ><el-descriptions-item label="联系电话">{{
            auth.user?.phone || '未填写'
          }}</el-descriptions-item
          ><el-descriptions-item label="账号状态"
            ><el-tag type="success">正常</el-tag></el-descriptions-item
          ></el-descriptions
        >
      </section>
      <section class="surface section-card">
        <h2>修改密码</h2>
        <p class="desc">为了账号安全，请定期更新密码。</p>
        <el-form label-position="top"
          ><el-form-item label="当前密码"
            ><el-input
              v-model="form.oldPassword"
              type="password"
              show-password
              autocomplete="current-password" /></el-form-item
          ><el-form-item label="新密码"
            ><el-input
              v-model="form.newPassword"
              type="password"
              show-password
              autocomplete="new-password" /></el-form-item
          ><el-form-item label="确认新密码"
            ><el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              @keyup.enter="save" /></el-form-item
          ><el-button type="primary" :loading="loading" @click="save">更新密码</el-button></el-form
        >
      </section>
    </div>
  </div>
</template>
<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  max-width: 980px;
}
.section-card h2 {
  margin: 0 0 8px;
  font-size: 17px;
}
.identity {
  display: flex;
  gap: 13px;
  align-items: center;
  margin: 20px 0;
}
.identity strong {
  font-size: 17px;
}
.identity p,
.desc {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.desc {
  margin-bottom: 22px;
}
@media (max-width: 760px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
