<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { api } from '@/util/request'
import { useAuthStore } from '@/stores/auth'
import type { Course, GradeRecord } from '@/types'
const auth = useAuthStore(),
  courses = ref<Course[]>([]),
  selected = ref<Course>(),
  records = ref<GradeRecord[]>([]),
  loading = ref(false),
  weights = reactive({ usualWeight: 20, midtermWeight: 30, finalWeight: 50 }),
  summary = ref<{ earnedCredits: number; requiredCredits: number; gpa: number } | null>(null),
  mine = ref<any[]>([])
const student = computed(() => auth.user?.role === 'STUDENT')
async function load() {
  loading.value = true
  try {
    if (student.value) {
      const x = await api<{
        records: any[]
        earnedCredits: number
        requiredCredits: number
        gpa: number
      }>({ url: '/api/grades/mine' })
      mine.value = x.records
      summary.value = x
    } else {
      courses.value = await api<Course[]>({ url: '/api/courses', params: { mine: true } })
    }
  } finally {
    loading.value = false
  }
}
async function select(course: Course) {
  selected.value = course
  const x = await api<{ course: Course; records: GradeRecord[] }>({
    url: `/api/courses/${course.id}/grades`,
  })
  selected.value = x.course
  records.value = x.records
  // Persisted weights must be shown when reopening a draft, rather than resetting to defaults.
  weights.usualWeight = x.course.usualWeight ?? 20
  weights.midtermWeight = x.course.midtermWeight ?? 30
  weights.finalWeight = x.course.finalWeight ?? 50
}
async function save() {
  if (!selected.value) return
  const sum = weights.usualWeight + weights.midtermWeight + weights.finalWeight
  if (sum !== 100) {
    ElMessage.warning('三项权重之和应为 100')
    return
  }
  await api({
    url: `/api/courses/${selected.value.id}/grades`,
    method: 'PUT',
    data: { ...weights, records: records.value },
  })
  ElMessage.success('成绩草稿已保存')
}
async function action(path: string, text: string) {
  if (!selected.value) return
  await ElMessageBox.confirm(`确认${text}当前课程成绩？`)
  await api({ url: `/api/courses/${selected.value.id}/grades/${path}`, method: 'POST' })
  ElMessage.success(`已${text}`)
  load()
  select(selected.value)
}
async function reject() {
  if (!selected.value) return
  const { value } = await ElMessageBox.prompt('请填写退回原因', '退回成绩')
  await api({
    url: `/api/courses/${selected.value.id}/grades/reject`,
    method: 'POST',
    data: { reason: value },
  })
  ElMessage.success('已退回')
  load()
}
onMounted(load)
</script>
<template>
  <div class="page">
    <PageHeader
      :title="student ? '我的成绩' : '成绩管理'"
      :description="student ? '查看已发布成绩、已获学分和 GPA' : '录入、提交、审核与发布课程成绩'"
    /><template v-if="student"
      ><section v-if="summary" class="summary">
        <article>
          <span>已获学分</span><strong>{{ summary.earnedCredits }}</strong>
        </article>
        <article>
          <span>培养要求</span><strong>{{ summary.requiredCredits }}</strong>
        </article>
        <article>
          <span>当前 GPA</span><strong>{{ summary.gpa }}</strong>
        </article>
      </section>
      <section class="surface section-card">
        <el-table :data="mine" v-loading="loading" stripe
          ><el-table-column prop="courseName" label="课程" min-width="180" /><el-table-column
            prop="semester"
            label="学期"
            min-width="130"
          /><el-table-column prop="credit" label="学分" width="80" /><el-table-column
            prop="score"
            label="成绩"
            width="90"
          /><el-table-column prop="examStatus" label="考试状态" min-width="100" /><template #empty
            ><div class="empty">暂无已发布成绩</div></template
          ></el-table
        >
      </section></template
    ><template v-else
      ><section class="surface section-card">
        <div class="course-strip">
          <span>选择课程</span
          ><el-select
            :model-value="selected?.id"
            placeholder="请选择课程"
            @change="(id: number) => select(courses.find((x) => x.id === id)!)"
            ><el-option
              v-for="course in courses"
              :key="course.id"
              :label="`${course.code} · ${course.name}`"
              :value="course.id" /></el-select
          ><el-tag v-if="selected" type="info">{{ selected.gradeStatus || 'DRAFT' }}</el-tag>
          <div class="grow" />
          <el-button v-if="selected" @click="save">保存草稿</el-button
          ><el-button
            v-if="selected?.gradeStatus === 'DRAFT'"
            type="primary"
            @click="action('submit', '提交')"
            >提交审核</el-button
          ><template v-if="auth.user?.role === 'ADMIN' && selected?.gradeStatus === 'SUBMITTED'"
            ><el-button type="warning" @click="reject">退回</el-button
            ><el-button type="primary" @click="action('approve', '审核通过')"
              >审核通过</el-button
            ></template
          ><el-button
            v-if="auth.user?.role === 'ADMIN' && selected?.gradeStatus === 'APPROVED'"
            type="success"
            @click="action('publish', '发布')"
            >发布成绩</el-button
          >
        </div>
        <template v-if="selected"
          ><div class="weights">
            <span>平时</span
            ><el-input-number v-model="weights.usualWeight" :min="0" :max="100" /><span>期中</span
            ><el-input-number v-model="weights.midtermWeight" :min="0" :max="100" /><span>期末</span
            ><el-input-number v-model="weights.finalWeight" :min="0" :max="100" /><span class="sum"
              >合计 {{ weights.usualWeight + weights.midtermWeight + weights.finalWeight }}%</span
            >
          </div>
          <el-table :data="records" v-loading="loading" stripe
            ><el-table-column prop="studentNo" label="学号" min-width="110" /><el-table-column
              prop="studentName"
              label="姓名"
              min-width="100" /><el-table-column label="平时" width="110"
              ><template #default="{ row }"
                ><el-input-number
                  v-model="row.usualScore"
                  :min="0"
                  :max="100"
                  controls-position="right" /></template></el-table-column
            ><el-table-column label="期中" width="110"
              ><template #default="{ row }"
                ><el-input-number
                  v-model="row.midtermScore"
                  :min="0"
                  :max="100"
                  controls-position="right" /></template></el-table-column
            ><el-table-column label="期末" width="110"
              ><template #default="{ row }"
                ><el-input-number
                  v-model="row.finalScore"
                  :min="0"
                  :max="100"
                  controls-position="right" /></template></el-table-column
            ><el-table-column prop="score" label="总评" width="80" /><el-table-column
              label="考试状态"
              width="130"
              ><template #default="{ row }"
                ><el-select v-model="row.examStatus"
                  ><el-option label="正常" value="NORMAL" /><el-option
                    label="缺考"
                    value="ABSENT" /><el-option label="缓考" value="DEFERRED" /><el-option
                    label="作弊"
                    value="CHEATING" /></el-select></template></el-table-column></el-table
        ></template>
        <div v-else class="empty">请选择一门课程后开始录入成绩</div>
      </section></template
    >
  </div>
</template>
<style scoped>
.summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.summary article {
  padding: 18px 22px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.summary span {
  display: block;
  color: var(--muted);
  font-size: 13px;
}
.summary strong {
  display: block;
  margin-top: 7px;
  font-size: 26px;
}
.course-strip,
.weights {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.course-strip > .el-select {
  width: 260px;
}
.grow {
  flex: 1;
}
.weights {
  padding: 12px;
  background: #f7faf8;
  border-radius: 8px;
  font-size: 13px;
}
.weights .el-input-number {
  width: 105px;
}
.sum {
  margin-left: auto;
  color: var(--muted);
}
@media (max-width: 760px) {
  .summary {
    grid-template-columns: 1fr;
  }
  .course-strip > .el-select {
    width: 100%;
  }
  .grow {
    display: none;
  }
}
</style>
