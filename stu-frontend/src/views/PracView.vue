<script setup lang="ts">
import MyButton from '@/components/MyButton.vue'
import { ref } from 'vue'
const a = ref(13)
const loginForm = ref({
  username: '',
  password: '',
})
//数据类型 列表数组【】 对象{}
const students = ref([
  { name: '张三', grade: '计科3班', sore: 11, total: 100 },
  { name: '李四', grade: '计科3班', sore: 21, total: 100 },
  { name: '王五', grade: '计科3班', sore: 31, total: 100 },
])

const incr = () => {
  console.log(a)
  a.value++
}
</script>

<template>
  <div>练习场</div>
  <my-button></my-button>
  <el-button type="primary">好看的按钮</el-button>
  <el-button type="warning" @click="incr">自增{{ a }}</el-button>
  <!-- 收集数据 -->
  <div>{{ loginForm }}</div>
  <el-form label-width="100" style="width: 400px">
    <el-form-item label="用户名">
      <el-input v-model="loginForm.username"></el-input>
    </el-form-item>
    <el-form-item label="密码">
      <el-input type="password" v-model="loginForm.password"></el-input>
    </el-form-item>
    <el-form-item>
      <el-button>登录</el-button>
    </el-form-item>
  </el-form>
  <!-- 数据展示  vfor 循环-->
  <div class="card" v-for="item in students">
    <div>{{ item.name }}</div>
    <div>{{ item.grade }}</div>
    <div>{{ item.sore }}</div>
  </div>
  <el-table :data="students">
    <el-table-column label="名称" prop="name"></el-table-column>
    <el-table-column label="班级" prop="grade"></el-table-column>
    <el-table-column label="分数" prop="sore"></el-table-column>
    <el-table-column label="总分数" prop="total"></el-table-column>
  </el-table>
</template>

<style scoped>
.card {
  border: solid 2px red;
  width: 100px;
  height: 100px;
}
</style>
