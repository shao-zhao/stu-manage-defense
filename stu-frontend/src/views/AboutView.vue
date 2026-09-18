<template>
  <div class="about">
    <h1>This is an about page</h1>
    <h1>{{ info }}</h1>
    <el-button type="primary" @click="send">发送</el-button>
  </div>
</template>
<script lang="ts" setup>
import request from '@/util/request.ts'
import { ref } from 'vue'
const info = ref('')
const send = async () => {
  const res = await request({
    url: '/api/time/info',
    method: 'get',
  })
  console.log(res)
  info.value = res.data
}
</script>
<style>
@media (min-width: 1024px) {
  .about {
    min-height: 100vh;
    display: flex;
    align-items: center;
  }
}
</style>
