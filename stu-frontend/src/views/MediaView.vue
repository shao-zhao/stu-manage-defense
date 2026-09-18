<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { api, assetUrl } from '@/util/request'
import type { Media } from '@/types'
import { useAuthStore } from '@/stores/auth'
const records = ref<Media[]>([]),
  loading = ref(false),
  dialog = ref(false),
  uploading = ref(false)
const form = reactive({
  title: '',
  url: '',
  kind: 'image' as 'image' | 'video',
  courseId: undefined as number | undefined,
})
const auth = useAuthStore()
function canDelete(item: Media) {
  return (
    auth.user?.role === 'ADMIN' || item.ownerId === auth.user?.id || item.authorId === auth.user?.id
  )
}
async function load() {
  loading.value = true
  try {
    records.value = await api<Media[]>({ url: '/api/media' })
  } finally {
    loading.value = false
  }
}
async function customUpload({ file }: UploadRequestOptions) {
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    const result = await api<{ url: string; kind: 'image' | 'video' }>({
      url: '/api/files',
      method: 'POST',
      data,
    })
    form.url = result.url
    form.kind = result.kind
    ElMessage.success('文件上传完成')
  } finally {
    uploading.value = false
  }
}
async function save() {
  if (!form.title || !form.url) {
    ElMessage.warning('请填写标题并上传资料')
    return
  }
  await api({ url: '/api/media', method: 'POST', data: form })
  dialog.value = false
  Object.assign(form, { title: '', url: '', kind: 'image', courseId: undefined })
  ElMessage.success('资料已发布')
  load()
}
async function remove(item: Media) {
  await ElMessageBox.confirm(`确认删除“${item.title}”？`)
  await api({ url: `/api/media/${item.id}`, method: 'DELETE' })
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
<template>
  <div class="page">
    <PageHeader title="教学资料" description="浏览并共享课程相关的图片与视频资料"
      ><el-button type="primary" :icon="Plus" @click="dialog = true"
        >上传资料</el-button
      ></PageHeader
    ><el-skeleton v-if="loading" :rows="6" animated />
    <section v-else-if="records.length" class="media-grid">
      <article v-for="item in records" :key="item.id" class="media-card">
        <div class="preview">
          <el-image
            v-if="item.kind === 'image'"
            :src="assetUrl(item.url)"
            fit="cover"
            :preview-src-list="[assetUrl(item.url)]"
          /><video v-else :src="assetUrl(item.url)" controls preload="metadata"></video>
        </div>
        <div class="media-meta">
          <div>
            <h2>{{ item.title }}</h2>
            <p>
              {{ item.author || item.authorName || '教学资料'
              }}<span v-if="item.createdAt"> · {{ item.createdAt }}</span>
            </p>
          </div>
          <el-button v-if="canDelete(item)" link type="danger" @click="remove(item)"
            >删除</el-button
          >
        </div>
      </article>
    </section>
    <div v-else class="surface empty">暂未上传教学资料</div>
    <el-dialog v-model="dialog" title="上传教学资料" width="560px"
      ><el-form label-width="80px"
        ><el-form-item label="资料标题"
          ><el-input v-model="form.title" placeholder="例如：课程实验演示" /></el-form-item
        ><el-form-item label="选择文件"
          ><el-upload :http-request="customUpload" :show-file-list="false" accept="image/*,video/*"
            ><el-button :icon="Upload" :loading="uploading">选择图片或视频</el-button></el-upload
          ><span v-if="form.url" class="uploaded">文件已上传</span></el-form-item
        ><el-form-item v-if="form.url" label="预览"
          ><el-image
            v-if="form.kind === 'image'"
            :src="assetUrl(form.url)"
            class="upload-preview"
            fit="cover" /><video
            v-else
            :src="assetUrl(form.url)"
            class="upload-preview"
            controls /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="save">发布资料</el-button></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.media-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}
.media-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.preview {
  height: 180px;
  background: #edf2ef;
}
.preview :deep(.el-image),
.preview video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.media-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 14px;
}
.media-meta h2 {
  margin: 0;
  font-size: 15px;
}
.media-meta p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.uploaded {
  margin-left: 12px;
  color: #176346;
  font-size: 13px;
}
.upload-preview {
  width: 100%;
  max-height: 220px;
  object-fit: contain;
  background: #f5f5f5;
}
@media (max-width: 1000px) {
  .media-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 600px) {
  .media-grid {
    grid-template-columns: 1fr;
  }
}
</style>
