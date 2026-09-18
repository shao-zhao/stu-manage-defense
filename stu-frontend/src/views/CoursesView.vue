<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { api } from '@/util/request'
import { useAuthStore } from '@/stores/auth'
import type { Course, Staff } from '@/types'
const auth = useAuthStore(),
  courses = ref<Course[]>([]),
  teachers = ref<Staff[]>([]),
  loading = ref(false),
  dialog = ref(false),
  editing = ref(false)
const query = reactive({ keyword: '', semester: '' })
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
const canEdit = computed(() => auth.user?.role !== 'STUDENT')
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
  editing.value = !!row
  dialog.value = true
}
async function save() {
  if (!form.code || !form.name || !form.semester) {
    ElMessage.warning('请补全课程编号、名称和学期')
    return
  }
  await api({
    url: editing.value ? `/api/courses/${form.id}` : '/api/courses',
    method: editing.value ? 'PUT' : 'POST',
    data: form,
  })
  dialog.value = false
  ElMessage.success('课程已保存')
  load()
}
async function publish(row: Course) {
  await ElMessageBox.confirm('发布后课程信息将锁定，确认发布？', '发布课程')
  await api({ url: `/api/courses/${row.id}/publish`, method: 'POST' })
  ElMessage.success('课程已发布')
  load()
}
async function enroll(row: Course) {
  await api({ url: `/api/courses/${row.id}/enroll`, method: 'POST' })
  ElMessage.success('选课成功')
  load()
}
async function withdraw(row: Course) {
  await ElMessageBox.confirm(`确认退出“${row.name}”？`, '退课')
  await api({ url: `/api/courses/${row.id}/withdraw`, method: 'POST' })
  ElMessage.success('已退课')
  load()
}
onMounted(load)
</script>
<template>
  <div class="page">
    <PageHeader
      :title="auth.user?.role === 'STUDENT' ? '课程中心' : '课程管理'"
      :description="
        auth.user?.role === 'STUDENT' ? '浏览已发布课程并完成选课' : '创建、维护和发布本学期课程'
      "
      ><el-button v-if="canEdit" type="primary" :icon="Plus" @click="open()"
        >新建课程</el-button
      ></PageHeader
    >
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
          /><el-button type="primary" @click="load">查询</el-button>
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
        ><el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{
              row.status === 'PUBLISHED' ? '已发布' : '未发布'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column fixed="right" label="操作" width="190"
          ><template #default="{ row }"
            ><template v-if="canEdit"
              ><el-button
                link
                type="primary"
                :disabled="row.status === 'PUBLISHED'"
                @click="open(row)"
                >编辑</el-button
              ><el-button
                v-if="row.status !== 'PUBLISHED'"
                link
                type="success"
                @click="publish(row)"
                >发布</el-button
              ></template
            ><template v-else
              ><el-button
                v-if="row.myEnrollmentStatus === 'ENROLLED'"
                link
                type="danger"
                @click="withdraw(row)"
                >退课</el-button
              ><el-button
                v-else
                link
                type="primary"
                :disabled="row.enrolled >= row.capacity"
                @click="enroll(row)"
                >选课</el-button
              ></template
            ></template
          ></el-table-column
        ><template #empty><div class="empty">暂无符合条件的课程</div></template></el-table
      >
    </section>
    <el-dialog v-model="dialog" :title="editing ? '编辑课程' : '新建课程'" width="680px"
      ><el-form label-width="85px"
        ><div class="grid">
          <el-form-item label="课程编号"><el-input v-model="form.code" /></el-form-item
          ><el-form-item label="课程名称"><el-input v-model="form.name" /></el-form-item
          ><el-form-item v-if="auth.user?.role === 'ADMIN'" label="任课教师"
            ><el-select v-model="form.teacherId" filterable
              ><el-option
                v-for="teacher in teachers"
                :key="teacher.id"
                :label="teacher.name"
                :value="teacher.id" /></el-select></el-form-item
          ><el-form-item label="学分"
            ><el-input-number v-model="form.credit" :min="0.5" :step="0.5" /></el-form-item
          ><el-form-item label="学时"
            ><el-input-number v-model="form.hours" :min="1" /></el-form-item
          ><el-form-item label="学期"><el-input v-model="form.semester" /></el-form-item
          ><el-form-item label="上课时间"
            ><el-input v-model="form.schedule" placeholder="周一 1-2 节" /></el-form-item
          ><el-form-item label="上课地点"><el-input v-model="form.location" /></el-form-item
          ><el-form-item label="课程容量"
            ><el-input-number v-model="form.capacity" :min="1"
          /></el-form-item>
        </div>
        <el-form-item label="课程简介"
          ><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
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
@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
