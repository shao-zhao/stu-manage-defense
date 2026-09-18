<template>
  <div>{{ queryCondition }}</div>
  <el-form inline>
    <el-form-item label="姓名">
      <el-input v-model="queryCondition.nameLike"></el-input>
    </el-form-item>
    <el-form-item label="手机号">
      <el-input v-model="queryCondition.phoneStart"></el-input>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="query">search</el-button>
    </el-form-item>
    <el-form-item>
      <el-button type="success" @click="insertVisible = true">add</el-button>
    </el-form-item>
  </el-form>

  <div>{{ result }}</div>
  <el-table :data="result">
    <el-table-column type="selection"></el-table-column>
    <el-table-column prop="id" label="id"></el-table-column>
    <el-table-column prop="grade" label="grade"></el-table-column>
    <el-table-column prop="name" label="name"></el-table-column>
    <el-table-column prop="earnCredits" label="earnCredits"></el-table-column>
    <el-table-column prop="phone" label="phone"></el-table-column>
    <el-table-column label="好看的学分">
      <template #default="{ row }">
        <el-tag :type="mapCredits[row.earnCredits]">{{ row.earnCredits }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column>
      <template #default="{ row }">
        <el-button size="small" type="warning" @click="modify(row.id)">修改</el-button>
        <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-dialog v-model="insertVisible">
    <div>新增{{ insertStudent }}</div>
    <el-form label-width="auto" style="width: 500px">
      <el-form-item label="name">
        <el-input v-model="insertStudent.name"></el-input>
      </el-form-item>
      <el-form-item label="grade">
        <el-select v-model="insertStudent.grade">
          <el-option label="计科1班" value="1"></el-option>
          <el-option label="计科2班" value="2"></el-option>
          <el-option label="计科3班" value="3"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="phone">
        <el-input v-model="insertStudent.phone"></el-input>
      </el-form-item>
    </el-form>
    <el-button type="primary" @click="insertSave">保存 真正新增</el-button>
  </el-dialog>
  <!-- 修改使用独立表单；取消时丢弃副本，不改动表格里的原始对象。 -->
  <el-dialog v-model="modifyVisible">
    <div>修改页面</div>
    <el-form label-width="auto" style="width: 500px">
      <el-form-item label="name">
        <el-input v-model="modifyStudent.name"></el-input>
      </el-form-item>
      <el-form-item label="grade">
        <el-select v-model="modifyStudent.grade">
          <el-option label="计科1班" value="1"></el-option>
          <el-option label="计科2班" value="2"></el-option>
          <el-option label="计科3班" value="3"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="phone">
        <el-input v-model="modifyStudent.phone"></el-input>
      </el-form-item>
    </el-form>
    <el-button type="primary" @click="modifySave">保存 真正新增</el-button>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeMount } from 'vue'
import request from '@/util/request.ts'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { isAxiosError } from 'axios'

type StudentEditForm = {
  id: number
  name: string
  grade: string
  phone: string
}

const editVisible = ref(false)
const modifyVisible = ref(false)

const editStudent = ref<StudentEditForm>({ id: 0, name: '', grade: '', phone: '' })

const openEdit = (row: StudentEditForm) => {
  // 创建新对象。若直接赋值为 row，输入框会立刻修改表格中的同一个对象。
  editStudent.value = {
    id: row.id,
    name: row.name ?? '',
    grade: row.grade ?? '',
    phone: row.phone ?? '',
  }
  editVisible.value = true
}

const mapCredits: Record<number, string> = {
  11: 'success',
  1: 'warning',
}
const queryCondition = ref({
  nameLike: '',
  phoneStart: '',
})
const result = ref([
  {
    earnCredits: 0,
    grade: '',
    id: 1,
    name: '',
    phone: '',
  },
])
const query = async () => {
  const res = await request({
    method: 'post',
    url: '/api/student/queryEX',
    data: queryCondition.value,
  })
  console.log(res)
  result.value = res.data
}
onMounted(async () => {
  //ElMessage.warning("自动运行")
  //帮用户点击
  await query()
})
const insertStudent = ref({
  id: 0,
  name: '',
  grade: '1',
  phone: '0',
  earnCredits: 0,
})
const insertVisible = ref(false)
const insertSave = async () => {
  const res = await request({
    url: '/api/student/insert',
    method: 'post',
    data: insertStudent.value,
  })
  // 帮用户关窗口
  insertVisible.value = false
  // 帮用户刷新
  await query()
}
const remove = async (id: number) => {
  await ElMessageBox.confirm('确认删除')
  const res = await request({
    method: 'delete',
    url: '/api/student/removeById/' + id,
  })
  await query()
}
const modifyStudent = ref({
  id: 0,
  name: '',
  grade: '1',
  phone: '0',
  earnCredits: 0,
})
const modify = async (id: number) => {
  modifyVisible.value = true
  ElMessage.success('将要修改' + id)

  const res = await request({
    url: '/api/student/findById/' + id,
    method: 'get',
  })
  modifyStudent.value = res.data
}

const modifySave = async () => {
  const res = await request({
    url: '/api/student/modify',
    method: 'put',
    data: modifyStudent.value,
  })
  modifyVisible.value = false
  await query();
}
</script>

<style scoped></style>
