<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import {
  Connection,
  DataAnalysis,
  Files,
  Loading,
  MostlyCloudy,
  RefreshRight,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import AppChart from '@/components/AppChart.vue'
import { api } from '@/util/request'
import type { DashboardData } from '@/types'

type SystemStatus = {
  database: { status: string; product?: string; version?: string }
  redis: { status: string }
  app: { javaVersion?: string; springBootVersion?: string }
}
const dashboard = ref<DashboardData | null>(null)
const system = ref<SystemStatus | null>(null)
const systemUnavailable = ref(false)
const loading = ref(false)
const error = ref('')
const firstRead = ref<DashboardData['cache'] | null>(null)
const secondRead = ref<DashboardData['cache'] | null>(null)
const statusType = (status?: string) =>
  status === 'UP' ? 'success' : status === 'DOWN' ? 'danger' : 'info'
const chartOption = computed<EChartsOption>(() => ({
  // 图表只绘制第二次真实 dashboard 响应中的选课数据，不在前端补造统计值。
  tooltip: { trigger: 'axis' },
  grid: { left: 38, right: 18, top: 28, bottom: 38 },
  xAxis: {
    type: 'category',
    data: dashboard.value?.courseEnrollment.map((item) => item.name) || [],
    axisLabel: { interval: 0, rotate: 25 },
  },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1ee' } } },
  series: [
    {
      type: 'bar',
      data: dashboard.value?.courseEnrollment.map((item) => item.value) || [],
      itemStyle: { color: '#176346', borderRadius: [4, 4, 0, 0] },
    },
  ],
}))
async function refresh() {
  loading.value = true
  error.value = ''
  systemUnavailable.value = false
  try {
    // 先把状态请求转为已处理结果，避免 dashboard 读取期间接口失败产生未处理的拒绝。
    const statusRequest = api<SystemStatus>({ url: '/api/system/status' }).then(
      (value) => ({ available: true as const, value }),
      () => ({ available: false as const }),
    )
    try {
      // 顺序读取：首次让后端填充缓存，第二次才可如实展示 Redis 命中与 TTL。
      const first = await api<DashboardData>({ url: '/api/dashboard' })
      firstRead.value = first.cache
      const second = await api<DashboardData>({ url: '/api/dashboard' })
      secondRead.value = second.cache
      dashboard.value = second
    } catch (reason) {
      error.value = (reason as Error).message
    }
    const statusResult = await statusRequest
    if (statusResult.available) system.value = statusResult.value
    else systemUnavailable.value = true
  } finally {
    loading.value = false
  }
}
onMounted(refresh)
</script>
<template>
  <div class="page">
    <PageHeader title="技术演示" description="展示正在运行的服务读数和操作入口，不使用模拟数据。"
      ><el-button type="primary" :icon="RefreshRight" :loading="loading" @click="refresh"
        >重新读取</el-button
      ></PageHeader
    ><el-alert
      v-if="error"
      type="error"
      :closable="false"
      show-icon
      title="无法读取技术状态，请检查服务后重试。"
      class="error"
    />
    <section class="status-grid">
      <article class="surface section-card">
        <div class="card-head">
          <el-icon><Connection /></el-icon>
          <h2>运行状态</h2>
        </div>
        <el-descriptions v-if="system" :column="1" size="small"
          ><el-descriptions-item label="数据库"
            ><el-tag :type="statusType(system.database.status)">{{
              system.database.status
            }}</el-tag>
            {{ system.database.product }} {{ system.database.version }}</el-descriptions-item
          ><el-descriptions-item label="Redis"
            ><el-tag :type="statusType(system.redis.status)">{{
              system.redis.status
            }}</el-tag></el-descriptions-item
          ><el-descriptions-item label="Java">{{
            system.app.javaVersion || '未返回'
          }}</el-descriptions-item
          ><el-descriptions-item label="Spring Boot">{{
            system.app.springBootVersion || '未返回'
          }}</el-descriptions-item></el-descriptions
        ><el-result
          v-else-if="systemUnavailable"
          icon="warning"
          title="系统状态暂不可用"
          sub-title="状态接口没有返回可用数据，请稍后重新读取。"
        /><el-skeleton v-else :rows="4" animated />
      </article>
      <article class="surface section-card">
        <div class="card-head">
          <el-icon><Loading /></el-icon>
          <h2>Redis 两次读取</h2>
        </div>
        <div class="reads">
          <div>
            <span>首次</span
            ><strong>{{
              firstRead?.hit ? '命中缓存' : firstRead ? '读取业务数据' : '等待读取'
            }}</strong
            ><small v-if="firstRead">TTL {{ firstRead.ttlSeconds }} 秒</small>
          </div>
          <div>
            <span>第二次</span
            ><strong>{{
              secondRead?.hit ? '命中缓存' : secondRead ? '读取业务数据' : '等待读取'
            }}</strong
            ><small v-if="secondRead">TTL {{ secondRead.ttlSeconds }} 秒</small>
          </div>
        </div>
        <p class="hint">缓存生成时间：{{ dashboard?.generatedAt || '后端未返回' }}</p>
      </article>
      <article class="surface section-card guide">
        <div class="card-head">
          <el-icon><Connection /></el-icon>
          <h2>SSE 实时通知</h2>
        </div>
        <p>
          顶部“实时已连接”表示浏览器已建立 HTTP
          流式推送连接。保持本页开启，教师提交成绩后，接收通知的管理员可观察通知角标变化。
        </p>
        <el-tag type="info">HTTP 流式推送</el-tag>
      </article>
    </section>
    <section class="surface section-card chart-card">
      <div class="card-head">
        <el-icon><DataAnalysis /></el-icon>
        <h2>ECharts 课程选课统计</h2>
      </div>
      <AppChart
        v-if="dashboard?.courseEnrollment.length"
        :option="chartOption"
        :loading="loading"
      />
      <div v-else class="empty">当前没有课程统计数据</div>
    </section>
    <section class="entry-grid">
      <RouterLink to="/media" class="entry"
        ><el-icon><Files /></el-icon>
        <div>
          <strong>媒体资料</strong>
          <p>上传并预览图片、视频资料</p>
        </div></RouterLink
      ><RouterLink to="/students" class="entry"
        ><el-icon><MostlyCloudy /></el-icon>
        <div>
          <strong>天气与 Excel</strong>
          <p>工作台查看真实天气，学生管理下载模板和导出</p>
        </div></RouterLink
      >
    </section>
  </div>
</template>
<style scoped>
.error {
  margin-bottom: 18px;
}
.status-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin-bottom: 18px;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  color: #176346;
}
.card-head h2 {
  margin: 0;
  color: var(--ink);
  font-size: 16px;
}
.reads {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.reads > div {
  padding: 12px;
  background: #f5f8f6;
  border-radius: 8px;
}
.reads span,
.reads small {
  display: block;
  color: var(--muted);
  font-size: 12px;
}
.reads strong {
  display: block;
  margin: 5px 0;
  font-size: 14px;
}
.hint,
.guide p {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.chart-card {
  min-height: 350px;
}
.entry-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin-top: 18px;
}
.entry {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  color: var(--ink);
  text-decoration: none;
}
.entry:hover {
  border-color: #8db7a6;
}
.entry .el-icon {
  padding: 10px;
  border-radius: 9px;
  background: #edf5f1;
  color: #176346;
  font-size: 20px;
}
.entry p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}
@media (max-width: 900px) {
  .status-grid,
  .entry-grid {
    grid-template-columns: 1fr;
  }
}
</style>
