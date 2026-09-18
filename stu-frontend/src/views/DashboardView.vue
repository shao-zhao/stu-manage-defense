<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import {
  Calendar,
  Cloudy,
  DataAnalysis,
  RefreshRight,
  Timer,
  Warning,
} from '@element-plus/icons-vue'
import AppChart from '@/components/AppChart.vue'
import PageHeader from '@/components/PageHeader.vue'
import { api } from '@/util/request'
import type { DashboardData } from '@/types'
type Weather = {
  available: boolean
  city?: string
  temperature?: number
  weatherCode?: number
  windSpeed?: number
  observedAt?: string
  source?: string
  message?: string
}
const loading = ref(true)
const dashboard = ref<DashboardData | null>(null)
const weather = ref<Weather | null>(null)
const error = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [d, w] = await Promise.all([
      api<DashboardData>({ url: '/api/dashboard' }),
      api<Weather>({ url: '/api/weather' }),
    ])
    dashboard.value = d
    weather.value = w
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
const departmentOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  color: ['#176346', '#5b967f', '#8db7a6', '#c4dbd1', '#d8eee3'],
  series: [
    {
      type: 'pie',
      radius: ['48%', '75%'],
      label: { formatter: '{b}\n{d}%' },
      data: dashboard.value?.creditsByDepartment || [],
    },
  ],
}))
const gradeOption = computed<EChartsOption>(() => ({
  grid: { left: 36, right: 12, top: 26, bottom: 30 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: dashboard.value?.gradeDistribution.map((x) => x.name) || [],
    axisTick: { show: false },
  },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1ee' } } },
  series: [
    {
      type: 'bar',
      data: dashboard.value?.gradeDistribution.map((x) => x.value) || [],
      barMaxWidth: 38,
      itemStyle: { color: '#176346', borderRadius: [5, 5, 0, 0] },
    },
  ],
}))
onMounted(load)
</script>
<template>
  <div class="page">
    <PageHeader title="工作台" description="查看教学事务和学习进度的最新动态"
      ><el-button :icon="RefreshRight" @click="load" :loading="loading"
        >刷新数据</el-button
      ></PageHeader
    ><el-alert v-if="error" type="error" :closable="false" show-icon class="load-error"
      ><template #title>数据暂时无法加载，请检查服务连接后重试。</template></el-alert
    ><template v-else
      ><section class="overview">
        <article v-for="(item, index) in dashboard?.stats || []" :key="item.label" class="metric">
          <div class="metric-icon" :class="`tone-${index}`">
            <el-icon><DataAnalysis /></el-icon>
          </div>
          <div>
            <p>{{ item.label }}</p>
            <strong
              >{{ item.value }}<small>{{ item.suffix }}</small></strong
            >
          </div>
        </article>
        <el-skeleton v-if="loading" v-for="i in 4" :key="i" animated
          ><template #template
            ><div class="metric">
              <el-skeleton-item variant="circle" style="width: 42px; height: 42px" />
              <div>
                <el-skeleton-item style="width: 68px" /><el-skeleton-item
                  style="width: 90px; height: 25px"
                />
              </div></div></template
        ></el-skeleton>
      </section>
      <section class="dashboard-grid">
        <article class="surface section-card chart-card">
          <div class="card-title">
            <div>
              <h2>学分完成情况</h2>
              <p>各院系已获得学分分布</p>
            </div>
          </div>
          <AppChart
            v-if="dashboard?.creditsByDepartment?.length"
            :option="departmentOption"
            :loading="loading"
          />
          <div v-else-if="!loading" class="empty">暂无可展示的学分数据</div>
        </article>
        <article class="surface section-card chart-card">
          <div class="card-title">
            <div>
              <h2>成绩分布</h2>
              <p>已发布课程的成绩区间</p>
            </div>
          </div>
          <AppChart
            v-if="dashboard?.gradeDistribution?.length"
            :option="gradeOption"
            :loading="loading"
          />
          <div v-else-if="!loading" class="empty">暂无可展示的成绩数据</div>
        </article>
        <article class="surface section-card activities">
          <div class="card-title">
            <div>
              <h2>最近动态</h2>
              <p>课程与成绩状态的变更记录</p>
            </div>
          </div>
          <el-skeleton v-if="loading" :rows="4" animated /><el-timeline
            v-else-if="dashboard?.recentActivities?.length"
            ><el-timeline-item
              v-for="activity in dashboard.recentActivities"
              :key="activity.id"
              :timestamp="activity.createdAt"
              placement="top"
              type="primary"
              ><strong>{{ activity.title }}</strong>
              <p>{{ activity.content }}</p></el-timeline-item
            ></el-timeline
          >
          <div v-else class="empty">暂无近期动态</div>
        </article>
        <aside class="right-stack">
          <article class="surface section-card weather">
            <div class="weather-heading">
              <span
                ><el-icon><Cloudy /></el-icon> 校园天气</span
              ><small v-if="weather?.available">{{ weather.city }}</small>
            </div>
            <template v-if="weather?.available"
              ><strong>{{ weather.temperature }}<small>°C</small></strong>
              <p>风速 {{ weather.windSpeed }} km/h</p>
              <footer>{{ weather.observedAt }} · {{ weather.source }}</footer></template
            ><template v-else-if="!loading"
              ><div class="weather-unavailable">
                <el-icon><Warning /></el-icon
                ><span>{{ weather?.message || '天气服务暂不可用' }}</span>
              </div></template
            ><el-skeleton v-else :rows="2" animated />
          </article>
          <article v-if="dashboard" class="surface section-card cache">
            <div>
              <el-icon><Timer /></el-icon><span>数据更新状态</span>
            </div>
            <p>{{ dashboard.cache.hit ? '已使用最新缓存结果' : '当前直接读取业务数据' }}</p>
            <small
              >来源：{{ dashboard.cache.backend
              }}<template v-if="dashboard.cache.hit">
                · {{ dashboard.cache.ttlSeconds }} 秒内有效</template
              ></small
            >
          </article>
        </aside>
      </section></template
    >
  </div>
</template>
<style scoped>
.load-error {
  margin-bottom: 18px;
}
.overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.metric {
  min-height: 102px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 13px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.metric-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: #edf5f1;
  color: #176346;
}
.tone-1 {
  background: #edf4fb;
  color: #3571a9;
}
.tone-2 {
  background: #fff5e5;
  color: #a36510;
}
.tone-3 {
  background: #f2eefb;
  color: #7653ac;
}
.metric p {
  margin: 0 0 5px;
  color: var(--muted);
  font-size: 13px;
}
.metric strong {
  font-size: 24px;
}
.metric strong small {
  margin-left: 3px;
  font-size: 12px;
  color: var(--muted);
}
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr)) 300px;
  gap: 18px;
}
.chart-card {
  min-height: 365px;
}
.card-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.card-title h2 {
  margin: 0 0 5px;
  font-size: 16px;
}
.card-title p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}
.activities {
  grid-column: span 2;
}
.activities :deep(.el-timeline) {
  margin: 21px 0 0;
}
.activities :deep(.el-timeline-item__timestamp) {
  font-size: 12px;
  color: var(--muted);
}
.activities p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.right-stack {
  display: grid;
  gap: 18px;
  align-content: start;
  grid-column: 3;
  grid-row: 1 / span 2;
}
.weather {
  min-height: 175px;
  background: linear-gradient(145deg, #fff, #edf6f1);
}
.weather-heading {
  display: flex;
  justify-content: space-between;
  color: #176346;
  font-size: 14px;
  font-weight: 700;
}
.weather-heading .el-icon {
  vertical-align: -2px;
}
.weather-heading small {
  color: var(--muted);
  font-weight: 400;
}
.weather strong {
  display: block;
  margin: 19px 0 4px;
  font-size: 40px;
  line-height: 1;
}
.weather strong small {
  font-size: 17px;
  font-weight: 400;
}
.weather p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.weather footer {
  margin-top: 19px;
  color: var(--muted);
  font-size: 11px;
}
.weather-unavailable {
  display: flex;
  gap: 8px;
  margin-top: 30px;
  color: var(--muted);
  font-size: 13px;
}
.cache > div {
  display: flex;
  gap: 7px;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
}
.cache > div .el-icon {
  color: #176346;
}
.cache p {
  margin: 13px 0 5px;
  font-size: 13px;
}
.cache small {
  color: var(--muted);
  font-size: 11px;
}
@media (max-width: 1150px) {
  .dashboard-grid {
    grid-template-columns: 1fr 1fr;
  }
  .right-stack {
    grid-column: span 2;
    grid-row: auto;
    grid-template-columns: 1fr 1fr;
  }
  .activities {
    grid-column: span 2;
  }
}
@media (max-width: 760px) {
  .overview {
    grid-template-columns: 1fr 1fr;
  }
  .dashboard-grid,
  .right-stack {
    display: block;
  }
  .dashboard-grid > *,
  .right-stack > * {
    margin-bottom: 14px;
  }
  .activities {
    min-height: 300px;
  }
  .chart-card {
    min-height: 330px;
  }
}
</style>
