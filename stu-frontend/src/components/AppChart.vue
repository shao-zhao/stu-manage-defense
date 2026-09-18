<script setup lang="ts">
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
const props = defineProps<{ option: echarts.EChartsOption; loading?: boolean }>()
const element = ref<HTMLElement>()
let chart: echarts.ECharts | undefined
function render() {
  if (chart) {
    chart.setOption(props.option, true)
    props.loading ? chart.showLoading('default', { text: '正在加载' }) : chart.hideLoading()
  }
}
onMounted(async () => {
  await nextTick()
  if (element.value) {
    chart = echarts.init(element.value)
    render()
    window.addEventListener('resize', resize)
  }
})
function resize() {
  chart?.resize()
}
watch(() => [props.option, props.loading], render, { deep: true })
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
</script>
<template><div ref="element" class="chart" aria-label="数据图表"></div></template>
