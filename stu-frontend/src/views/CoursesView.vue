<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus, SwitchButton } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { api } from '@/util/request'
import { useAuthStore } from '@/stores/auth'
import type { Course, Staff } from '@/types'

const auth = useAuthStore()
const courses = ref<Course[]>([])
const teachers = ref<Staff[]>([])
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const editing = ref(false)
const rowBusy = ref<number | null>(null)
const query = reactive({ keyword: '', semester: '', status: '' })
const empty = () => ({
  id: 0,
  code: '',
  name: '',
  teacherId: 0,
  credit: 2,
  hours: 32,
  semester: '2026-2027-1',
  schedule: '',
  location: '',
  capacity: 40,
  description: '',
  coverUrl: '',
})
const form = reactive(empty())
const isStudent = computed(() => auth.user?.role === 'STUDENT')
// 已发布或停开的课程只允许补充说明类字段，最终限制仍由后端规则执行。
const restrictedEdit = computed(
  () => editing.value && (form as { status?: Course['status'] }).status !== 'UNPUBLISHED',
)

function statusText(status: Course['status']) {
  return { UNPUBLISHED: '未发布', PUBLISHED: '开放选课', CLOSED: '停止选课' }[status]
}
function statusType(status: Course['status']) {
  return status === 'PUBLISHED' ? 'success' : status === 'CLOSED' ? 'warning' : 'info'
}
async function load() {
  loading.value = true
  try {
    courses.value = await api<Course[]>({ url: '/api/courses', params: query })
    if (auth.user?.role === 'ADMIN')
      teachers.value = await api<Staff[]>({ url: '/api/staff/teachers' })
  } finally {
    loading.value = false
  }
}
function open(row?: Course) {
  Object.assign(form, row ? { ...row } : empty())
  editing.value = Boolean(row)
  dialog.value = true
}
async function save() {
  if (!form.code || !form.name || !form.semester) {
    ElMessage.warning('请补全课程编号、名称和学期')
    return
  }
  saving.value = true
  try {
    await api({
      url: editing.value ? `/api/courses/${form.id}` : '/api/courses',
      method: editing.value ? 'PUT' : 'POST',
      data: form,
    })
    dialog.value = false
    ElMessage.success(restrictedEdit.value ? '课程说明已更新' : '课程已保存')
    await load()
  } finally {
    saving.value = false
  }
}
async function mutate(row: Course, action: 'publish' | 'close' | 'reopen' | 'delete') {
  // 课程状态转换调用真实接口；刷新列表后以服务端返回的状态为准。
  const labels = { publish: '发布', close: '停止选课', reopen: '恢复选课', delete: '删除' }
  const hints = {
    publish: '发布后核心教学信息将锁定，确认发布？',
    close: '停止新选课，但已有选课和成绩记录会保留，确认继续？',
    reopen: '仅成绩仍为草稿的停开课程可恢复选课，确认继续？',
    delete: '仅无选课、成绩、资料关联的未发布课程可删除，确认继续？',
  }
  try {
    await ElMessageBox.confirm(hints[action], labels[action])
  } catch {
    return
  }
  rowBusy.value = row.id
  try {
    await api({
      url: action === 'delete' ? `/api/courses/${row.id}` : `/api/courses/${row.id}/${action}`,
      method: action === 'delete' ? 'DELETE' : 'POST',
    })
    ElMessage.success(`课程已${labels[action]}`)
    await load()
  } finally {
    rowBusy.value = null
  }
}
async function enroll(row: Course) {
  if (row.status === 'CLOSED') {
    ElMessage.info('该课程已停止选课，已有记录仍会保留')
    return
  }
  rowBusy.value = row.id
  try {
    await api({ url: `/api/courses/${row.id}/enroll`, method: 'POST' })
    ElMessage.success('选课成功')
    await load()
  } finally {
    rowBusy.value = null
  }
}
async function withdraw(row: Course) {
  try {
    await ElMessageBox.confirm(`确认退出“${row.name}”？`, '退课')
  } catch {
    return
  }
  rowBusy.value = row.id
  try {
    await api({ url: `/api/courses/${row.id}/withdraw`, method: 'POST' })
    ElMessage.success('已退课')
    await load()
  } finally {
    rowBusy.value = null
  }
}
onMounted(load)
</script>
<template>
  <div class="page">
    <PageHeader
      :title="isStudent ? '课程中心' : '课程管理'"
      :description="
        isStudent
          ? '浏览课程状态并完成选课，停开课程保留已有记录'
          : '维护课程，发布后仅可修改地点、简介和封面等说明信息'
      "
    >
      <el-button v-if="!isStudent" type="primary" :icon="Plus" @click="open()">新建课程</el-button>
    </PageHeader>
    <section class="surface section-card">
      <div class="table-toolbar">
        <div class="form-inline">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="课程名称或编号"
            @keyup.enter="load"
          /><el-input
            v-model="query.semester"
            clearable
            placeholder="学期，如 2026-2027-1"
            @keyup.enter="load"
          /><el-select v-model="query.status" clearable placeholder="课程状态" @change="load"
            ><el-option label="未发布" value="UNPUBLISHED" /><el-option
              label="开放选课"
              value="PUBLISHED" /><el-option label="停止选课" value="CLOSED" /></el-select
          ><el-button type="primary" @click="load">查询</el-button>
        </div>
      </div>
      <el-table :data="courses" v-loading="loading" stripe
        ><el-table-column prop="code" label="课程编号" min-width="110" /><el-table-column
          prop="name"
          label="课程名称"
          min-width="150"
        /><el-table-column prop="teacherName" label="任课教师" min-width="100" /><el-table-column
          prop="credit"
          label="学分"
          width="70"
        /><el-table-column prop="semester" label="学期" min-width="115" /><el-table-column
          prop="schedule"
          label="上课时间"
          min-width="140"
        /><el-table-column prop="location" label="地点" min-width="100" /><el-table-column
          label="容量"
          min-width="90"
          ><template #default="{ row }"
            >{{ row.enrolled }} / {{ row.capacity }}</template
          ></el-table-column
        ><el-table-column label="状态" width="105"
          ><template #default="{ row }"
            ><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template
          ></el-table-column
        ><el-table-column fixed="right" label="操作" min-width="220"
          ><template #default="{ row }"
            ><template v-if="!isStudent"
              ><el-button
                link
                type="primary"
                :icon="EditPen"
                :loading="rowBusy === row.id"
                @click="open(row)"
                >编辑</el-button
              ><el-button
                v-if="row.status === 'UNPUBLISHED'"
                link
                type="success"
                @click="mutate(row, 'publish')"
                >发布</el-button
              ><el-button
                v-if="row.status === 'UNPUBLISHED'"
                link
                type="danger"
                :icon="Delete"
                @click="mutate(row, 'delete')"
                >删除</el-button
              ><el-button
                v-if="row.status === 'PUBLISHED'"
                link
                type="warning"
                :icon="SwitchButton"
                @click="mutate(row, 'close')"
                >停止选课</el-button
              ><el-button
                v-if="row.status === 'CLOSED'"
                link
                type="success"
                @click="mutate(row, 'reopen')"
                >恢复选课</el-button
              ></template
            ><template v-else
              ><el-button
                v-if="row.myEnrollmentStatus === 'ENROLLED'"
                link
                type="danger"
                :loading="rowBusy === row.id"
                @click="withdraw(row)"
                >退课</el-button
              ><el-tooltip
                v-else-if="row.status === 'CLOSED'"
                content="该课程已停止新选课，已有选课记录仍会保留"
                ><el-button link disabled>停止选课</el-button></el-tooltip
              ><el-button
                v-else
                link
                type="primary"
                :loading="rowBusy === row.id"
                :disabled="row.enrolled >= row.capacity"
                @click="enroll(row)"
                >选课</el-button
              ></template
            ></template
          ></el-table-column
        ><template #empty><div class="empty">暂无符合条件的课程</div></template></el-table
      >
    </section>
    <el-dialog
      v-model="dialog"
      :title="editing ? '编辑课程' : '新建课程'"
      width="680px"
      destroy-on-close
      ><el-alert
        v-if="restrictedEdit"
        type="info"
        :closable="false"
        show-icon
        title="课程已发布或停开：编号、名称、教师、学分、学时、学期、时间与容量均已锁定，只能更新地点、简介和封面。"
        class="lock-tip"
      /><el-form label-width="85px"
        ><div class="grid">
          <el-form-item label="课程编号"
            ><el-input v-model="form.code" :disabled="restrictedEdit" /></el-form-item
          ><el-form-item label="课程名称"
            ><el-input v-model="form.name" :disabled="restrictedEdit" /></el-form-item
          ><el-form-item v-if="auth.user?.role === 'ADMIN'" label="任课教师"
            ><el-select v-model="form.teacherId" filterable :disabled="restrictedEdit"
              ><el-option
                v-for="teacher in teachers"
                :key="teacher.id"
                :label="teacher.name"
                :value="teacher.id" /></el-select></el-form-item
          ><el-form-item label="学分"
            ><el-input-number
              v-model="form.credit"
              :min="0.5"
              :step="0.5"
              :disabled="restrictedEdit" /></el-form-item
          ><el-form-item label="学时"
            ><el-input-number
              v-model="form.hours"
              :min="1"
              :disabled="restrictedEdit" /></el-form-item
          ><el-form-item label="学期"
            ><el-input v-model="form.semester" :disabled="restrictedEdit" /></el-form-item
          ><el-form-item label="上课时间"
            ><el-input v-model="form.schedule" :disabled="restrictedEdit" /></el-form-item
          ><el-form-item label="上课地点"><el-input v-model="form.location" /></el-form-item
          ><el-form-item label="课程容量"
            ><el-input-number
              v-model="form.capacity"
              :min="1"
              :disabled="restrictedEdit" /></el-form-item
          ><el-form-item label="封面地址"><el-input v-model="form.coverUrl" /></el-form-item>
        </div>
        <el-form-item label="课程简介"
          ><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}
.lock-tip {
  margin-bottom: 16px;
}
@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
