<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <el-container class="out-container">
    <el-header style="background: red">学生管理系统</el-header>
    <el-container>
      <el-aside style="background: beige">
        <el-menu router>
          <el-menu-item index="/home">Home</el-menu-item>
          <el-menu-item index="/about">About</el-menu-item>
          <el-menu-item index="/student">学生管理</el-menu-item>
          <el-menu-item index="/staff">职工管理</el-menu-item>
          <el-menu-item index="/prac">练习</el-menu-item>
        </el-menu>

      </el-aside>
      <el-main style="background: #2c3e50">
        <RouterView style="border: solid 2px red" />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.out-container {
  height: 100vh;
}
</style>
