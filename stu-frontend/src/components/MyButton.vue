<script setup lang="ts">
const go = () => {
  window.alert('点击了')
}
</script>

<template>
  <button class="core-button" @click="go">我的按钮</button>
</template>

<style scoped>
.core-button {
  background: darkcyan;
  color: darkorchid;
}
</style>
