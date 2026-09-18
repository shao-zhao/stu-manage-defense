<template>
  <div>员工管理</div>
  <el-form inline>
    <el-form-item label="姓名">
      <el-input v-model="queryCondition.nameLike"></el-input>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="query">search</el-button>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="insertSave">add</el-button>
    </el-form-item>
  </el-form>
  <el-table :data="records">
    <el-table-column label="username" prop="username"></el-table-column>
    <el-table-column label="password" prop="password"></el-table-column>
    <el-table-column label="photo" prop="photo"></el-table-column>
    <el-table-column label="photo">
      <template #default="{ row }">
        <el-image :src="row.photo" style="height: 60px"></el-image>
      </template>
    </el-table-column>
    <el-table-column label="操作">
      <template #default="row">
        <el-button type="primary" size="small">重置密码</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    v-model:current-page="queryCondition.pageNum"
    v-model:page-size="queryCondition.pageSize"
    :total="total"
    @current-change="query"
  ></el-pagination>
  <el-form>
    <el-form-item label="username">
      <el-input v-model="insertStaff.username"></el-input>
    </el-form-item>
    <el-form-item label="photo">
      <el-upload
        list-type="picture-card"
        action="http://localhost:9090/api/file/uploadOne"
        method="post"
        :limit="1"
        v-model:file-list="myPhoto"
      >
        <el-button>upload</el-button>
      </el-upload>
    </el-form-item>
  </el-form>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import request from '@/util/request.ts'
import { ElMessage } from 'element-plus'

const queryCondition = ref({
  pageNum: 2,
  pageSize: 2,
  nameLike: '',
})
const total = ref(0)
const records = ref([
  {
    id: 0,
    password: '',
    photo: null,
    username: '',
  },
])
const query = async () => {
  const res = await request({
    url: '/api/staff/query',
    method: 'post',
    data: queryCondition.value,
  })

  records.value = res.data.records
  total.value = res.data.total
}
onMounted(async () => {
  //ElMessage.warning("自动运行")
  //帮用户点击
  await query()
})
const insertStaff = ref({
  photo: '',
  username: '',
})
const myPhoto = ref([]) as any
const insertSave = async () => {
  if (myPhoto.value[0].response) {
    insertStaff.value.photo = myPhoto.value[0].response.data
  }
  const res = await request({
    url: '/api/staff/insert',
    method: 'post',
    data: insertStaff.value,
  })
  await query()
}
</script>

<style></style>