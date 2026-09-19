<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { Download, Plus, Upload } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { api, download } from '@/util/request'
import type { Student } from '@/types'
const loading = ref(false),
  total = ref(0),
  records = ref<Student[]>([]),
  dialog = ref(false),
  editing = ref(false)
const query = reactive({ page: 1, size: 10, keyword: '', status: '' })
const empty = () => ({
  id: 0,
  studentNo: '',
  name: '',
  gender: '男',
  phone: '',
  department: '',
  major: '',
  className: '',
  enrollmentYear: new Date().getFullYear(),
  status: 'ENABLED',
})
const form = reactive(empty())
async function load() {
  loading.value = true
  try {
    const x = await api<{ records: Student[]; total: number }>({
      url: '/api/students',
      params: query,
    })
    records.value = x.records
    total.value = x.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.page = 1
  load()
}
function open(row?: Student) {
  Object.assign(form, row ? { ...row } : empty())
  editing.value = !!row
  dialog.value = true
}
async function save() {
  if (!form.name || !form.department || !form.major || !form.className) {
    ElMessage.warning('请补全必填信息')
    return
  }
  await api({
    url: editing.value ? `/api/students/${form.id}` : '/api/students',
    method: editing.value ? 'PUT' : 'POST',
    data: form,
  })
  dialog.value = false
  ElMessage.success('已保存')
  load()
}
async function state(row: Student) {
  const status = row.status === 'ENABLED' ? 'FROZEN' : 'ENABLED'
  await ElMessageBox.confirm(`确认${status === 'FROZEN' ? '冻结' : '启用'} ${row.name} 的账号？`)
  await api({ url: `/api/students/${row.id}/status`, method: 'PUT', data: { status } })
  load()
}
async function reset(row: Student) {
  await ElMessageBox.confirm(`确认重置 ${row.name} 的密码？`)
  await api({ url: `/api/students/${row.id}/reset-password`, method: 'POST' })
  ElMessage.success('密码已重置为初始密码')
}
async function upload({ file }: UploadRequestOptions) {
  const data = new FormData()
  data.append('file', file)
  await api({ url: '/api/students/import', method: 'POST', data })
  ElMessage.success('导入成功')
  load()
}
onMounted(load)
</script>
<template>
  <div class="page">
    <PageHeader title="学生管理" description="维护学生档案、账号状态与学业基础信息"
      ><div class="actions">
        <el-upload :http-request="upload" :show-file-list="false" accept=".xlsx"
          ><el-button :icon="Upload">导入名单</el-button></el-upload
        ><el-button
          :icon="Download"
          @click="download('/api/students/template', '学生导入模板.xlsx')"
          >模板</el-button
        ><el-button :icon="Download" @click="download('/api/students/export', '学生名单.xlsx')"
          >导出</el-button
        ><el-button type="primary" :icon="Plus" @click="open()">新增学生</el-button>
      </div></PageHeader
    >
    <section class="surface section-card">
      <div class="table-toolbar">
        <div class="form-inline">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="按姓名或学号搜索"
            @keyup.enter="search"
          /><el-select v-model="query.status" clearable placeholder="账号状态" @change="search"
            ><el-option label="正常" value="ENABLED" /><el-option
              label="冻结"
              value="FROZEN" /><el-option label="暂停" value="SUSPENDED" /></el-select
          ><el-button type="primary" @click="search">查询</el-button>
        </div>
      </div>
      <el-table :data="records" v-loading="loading" stripe
        ><el-table-column prop="studentNo" label="学号" min-width="110" /><el-table-column
          prop="name"
          label="姓名"
          min-width="90"
        /><el-table-column prop="department" label="院系" min-width="120" /><el-table-column
          prop="major"
          label="专业"
          min-width="110"
        /><el-table-column prop="className" label="班级" min-width="100" /><el-table-column
          label="学业情况"
          min-width="130"
          ><template #default="{ row }"
            >{{ row.earnedCredits }}/{{ row.requiredCredits }} 学分 · GPA {{ row.gpa }}</template
          ></el-table-column
        ><el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 'ENABLED' ? 'success' : 'warning'">{{
              row.status === 'ENABLED' ? '正常' : row.status === 'FROZEN' ? '冻结' : '暂停'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column fixed="right" label="操作" width="190"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="open(row)">编辑</el-button
            ><el-button link @click="state(row)">{{
              row.status === 'ENABLED' ? '冻结' : '启用'
            }}</el-button
            ><el-button link type="danger" @click="reset(row)">重置密码</el-button></template
          ></el-table-column
        ><template #empty><div class="empty">没有找到学生记录</div></template></el-table
      ><el-pagination
        class="pager"
        layout="total, prev, pager, next"
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        @current-change="load"
      />
    </section>
    <el-dialog v-model="dialog" :title="editing ? '编辑学生' : '新增学生'"
      ><el-form label-width="85px"
        ><div class="grid">
          <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item
          ><el-form-item label="性别"
            ><el-select v-model="form.gender"
              ><el-option label="男" value="男" /><el-option
                label="女"
                value="女" /></el-select></el-form-item
          ><el-form-item label="院系"><el-input v-model="form.department" /></el-form-item
          ><el-form-item label="专业"><el-input v-model="form.major" /></el-form-item
          ><el-form-item label="班级"><el-input v-model="form.className" /></el-form-item
          ><el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item
          ><el-form-item label="入学年份"
            ><el-input-number v-model="form.enrollmentYear" :min="2000" :max="2100"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pager {
  justify-content: flex-end;
  margin-top: 18px;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}
@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .actions {
    width: 100%;
  }
}
</style>
