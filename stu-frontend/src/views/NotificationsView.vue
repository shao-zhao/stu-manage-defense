<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { api } from '@/util/request'
import type { Notification } from '@/types'
const items = ref<Notification[]>([]),
  loading = ref(false)
async function load() {
  loading.value = true
  try {
    items.value = await api<Notification[]>({ url: '/api/notifications' })
  } finally {
    loading.value = false
  }
}
async function read(item: Notification) {
  if (item.read) return
  await api({ url: `/api/notifications/${item.id}/read`, method: 'PUT' })
  item.read = true
  window.dispatchEvent(new CustomEvent('notification-read'))
}
onMounted(() => {
  load()
  window.addEventListener('data-refresh', load)
})
onBeforeUnmount(() => window.removeEventListener('data-refresh', load))
</script>
<template>
  <div class="page">
    <PageHeader title="通知中心" description="查看课程、选课与成绩相关的最新通知" />
    <section class="surface section-card">
      <el-skeleton v-if="loading" :rows="5" animated />
      <div v-else-if="items.length" class="notes">
        <button
          v-for="item in items"
          :key="item.id"
          class="note"
          :class="{ unread: !item.read }"
          @click="read(item)"
        >
          <span class="dot"></span>
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.content }}</p>
            <small>{{ item.createdAt }}</small>
          </div>
        </button>
      </div>
      <div v-else class="empty">暂时没有通知</div>
    </section>
  </div>
</template>
<style scoped>
.note {
  display: grid;
  grid-template-columns: 12px 1fr;
  gap: 10px;
  width: 100%;
  padding: 17px 8px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.note:hover {
  background: #f7faf8;
}
.dot {
  width: 7px;
  height: 7px;
  margin-top: 8px;
  border-radius: 50%;
  background: transparent;
}
.unread .dot {
  background: #176346;
}
.note strong {
  font-size: 14px;
}
.note p {
  margin: 5px 0;
  color: var(--muted);
  font-size: 13px;
}
.note small {
  color: var(--muted);
  font-size: 12px;
}
</style>
