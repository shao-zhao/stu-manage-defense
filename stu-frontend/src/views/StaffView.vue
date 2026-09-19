<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { api } from '@/util/request'
import type { Staff } from '@/types'
const query = reactive({ page: 1, size: 10, keyword: '' }),
  records = ref<Staff[]>([]),
  total = ref(0),
  loading = ref(false),
  dialog = ref(false),
  editing = ref(false)
const empty = () => ({
  id: 0,
  username: '',
  name: '',
  role: 'TEACHER' as 'TEACHER' | 'ADMIN',
  department: '',
  phone: '',
  status: 'ENABLED',
})
const form = reactive(empty())
async function load() {
  loading.value = true
  try {
    const d = await api<{ records: Staff[]; total: number }>({ url: '/api/staff', params: query })
    records.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.page = 1
  load()
}
function open(row?: Staff) {
  Object.assign(form, row ? { ...row } : empty())
  editing.value = !!row
  dialog.value = true
}
async function save() {
  if (!form.username || !form.name) {
    ElMessage.warning('请填写账号和姓名')
    return
  }
  await api({
    url: editing.value ? `/api/staff/${form.id}` : '/api/staff',
    method: editing.value ? 'PUT' : 'POST',
    data: form,
  })
  dialog.value = false
  ElMessage.success('已保存')
  load()
}
async function toggle(row: Staff) {
  await ElMessageBox.confirm(`确认${row.status === 'ENABLED' ? '冻结' : '启用'}该账号？`)
  await api({
    url: `/api/staff/${row.id}/status`,
    method: 'PUT',
    data: { status: row.status === 'ENABLED' ? 'FROZEN' : 'ENABLED' },
  })
  load()
}
async function reset(row: Staff) {
  await ElMessageBox.confirm(`确认重置 ${row.name} 的密码？`)
  await api({ url: `/api/staff/${row.id}/reset-password`, method: 'POST' })
  ElMessage.success('密码已重置为初始密码')
}
onMounted(load)
</script>
<template>
  <div class="page">
    <PageHeader title="教职工管理" description="管理教师和教学管理人员的基础信息与账号"
      ><el-button type="primary" :icon="Plus" @click="open()">新增教职工</el-button></PageHeader
    >
    <section class="surface section-card">
      <div class="table-toolbar">
        <div class="form-inline">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="按姓名或账号搜索"
            @keyup.enter="search"
          /><el-button type="primary" @click="search">查询</el-button>
        </div>
      </div>
      <el-table :data="records" v-loading="loading" stripe
        ><el-table-column prop="username" label="账号" min-width="120" /><el-table-column
          prop="name"
          label="姓名"
          min-width="100"
        /><el-table-column label="角色" width="105"
          ><template #default="{ row }">{{
            row.role === 'TEACHER' ? '教师' : '教学管理'
          }}</template></el-table-column
        ><el-table-column prop="department" label="院系/部门" min-width="140" /><el-table-column
          prop="phone"
          label="联系电话"
          min-width="130"
        /><el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 'ENABLED' ? 'success' : 'warning'">{{
              row.status === 'ENABLED' ? '正常' : '冻结'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column fixed="right" label="操作" width="190"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="open(row)">编辑</el-button
            ><el-button link @click="toggle(row)">{{
              row.status === 'ENABLED' ? '冻结' : '启用'
            }}</el-button
            ><el-button link type="danger" @click="reset(row)">重置密码</el-button></template
          ></el-table-column
        ><template #empty><div class="empty">没有找到教职工记录</div></template></el-table
      ><el-pagination
        class="pager"
        layout="total, prev, pager, next"
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        @current-change="load"
      />
    </section>
    <el-dialog v-model="dialog" :title="editing ? '编辑教职工' : '新增教职工'"
      ><el-form label-width="90px"
        ><el-form-item label="账号"
          ><el-input v-model="form.username" :disabled="editing" /></el-form-item
        ><el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item
        ><el-form-item label="角色"
          ><el-select v-model="form.role"
            ><el-option label="教师" value="TEACHER" /><el-option
              label="教学管理"
              value="ADMIN" /></el-select></el-form-item
        ><el-form-item label="院系/部门"><el-input v-model="form.department" /></el-form-item
        ><el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.pager {
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
