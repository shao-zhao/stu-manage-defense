<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { api } from '@/util/request'
import type { Enrollment } from '@/types'
const data = ref<Enrollment[]>([]),
  loading = ref(false)
async function load() {
  loading.value = true
  try {
    data.value = await api<Enrollment[]>({ url: '/api/enrollments/mine' })
  } finally {
    loading.value = false
  }
}
async function withdraw(row: Enrollment) {
  await ElMessageBox.confirm(`确认退出“${row.courseName}”？`)
  await api({ url: `/api/courses/${row.courseId}/withdraw`, method: 'POST' })
  ElMessage.success('已退课')
  load()
}
onMounted(load)
</script>
<template>
  <div class="page">
    <PageHeader title="我的选课" description="查看本学期已选课程与上课安排" />
    <section class="surface section-card">
      <el-table :data="data" v-loading="loading" stripe
        ><el-table-column prop="courseName" label="课程" min-width="180" /><el-table-column
          prop="teacherName"
          label="教师"
          min-width="100"
        /><el-table-column prop="semester" label="学期" min-width="120" /><el-table-column
          prop="credit"
          label="学分"
          width="80"
        /><el-table-column prop="schedule" label="上课时间" min-width="150" /><el-table-column
          prop="location"
          label="地点"
          min-width="110"
        /><el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 'ENROLLED' ? 'success' : 'info'">{{
              row.status === 'ENROLLED' ? '已选' : '已退'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button v-if="row.status === 'ENROLLED'" link type="danger" @click="withdraw(row)"
              >退课</el-button
            ></template
          ></el-table-column
        ><template #empty
          ><div class="empty">暂未选择课程，前往课程中心开始选课</div></template
        ></el-table
      >
    </section>
  </div>
</template>
