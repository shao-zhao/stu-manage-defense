<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { api } from '@/util/request'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Collection,
  DataAnalysis,
  Files,
  Fold,
  HomeFilled,
  Menu as MenuIcon,
  Reading,
  School,
  Setting,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
const auth = useAuthStore()
const router = useRouter()
const collapsed = ref(false)
const unread = ref(0)
let aborter: AbortController | undefined
let reconnectTimer: number | undefined
let reconnectAttempts = 0
let eventsEnabled = true
const roleText = { ADMIN: '教学管理', TEACHER: '教师', STUDENT: '学生' }
const menus = computed(() => [
  { path: '/dashboard', label: '工作台', icon: HomeFilled },
  ...(auth.user?.role === 'ADMIN'
    ? [
        { path: '/students', label: '学生管理', icon: UserFilled },
        { path: '/staff', label: '教职工管理', icon: UserFilled },
      ]
    : []),
  {
    path: '/courses',
    label: auth.user?.role === 'STUDENT' ? '课程中心' : '课程管理',
    icon: Reading,
  },
  ...(auth.user?.role === 'STUDENT'
    ? [{ path: '/enrollments', label: '我的选课', icon: Collection }]
    : []),
  {
    path: '/grades',
    label: auth.user?.role === 'STUDENT' ? '我的成绩' : '成绩管理',
    icon: DataAnalysis,
  },
  { path: '/media', label: '教学资料', icon: Files },
  { path: '/notifications', label: '通知中心', icon: Bell },
  { path: '/profile', label: '个人设置', icon: Setting },
])
async function logout() {
  await auth.logout()
  router.replace('/login')
}
async function startEvents() {
  if (!eventsEnabled || !localStorage.getItem('stu_manage_token')) return
  aborter = new AbortController()
  try {
    const response = await fetch(
      `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090'}/api/events`,
      {
        headers: { Authorization: `Bearer ${localStorage.getItem('stu_manage_token')}` },
        signal: aborter.signal,
      },
    )
    if (!response.ok || !response.body) throw new Error('通知连接未建立')
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const part = await reader.read()
      if (part.done) break
      buffer += decoder.decode(part.value, { stream: true })
      const events = buffer.replace(/\r\n/g, '\n').split('\n\n')
      buffer = events.pop() || ''
      events.forEach((event) => {
        const type = event
          .split('\n')
          .find((line) => line.startsWith('event:'))
          ?.slice(6)
          .trim()
        if (type === 'notification') {
          unread.value++
          ElMessage.info('收到一条新通知')
        }
        if (type === 'refresh') window.dispatchEvent(new CustomEvent('data-refresh'))
      })
    }
    // The server may close an idle stream. Resume it while the layout is active.
    if (eventsEnabled) reconnectTimer = window.setTimeout(startEvents, 1500)
    reconnectAttempts = 0
  } catch (error) {
    if ((error as DOMException).name === 'AbortError' || !eventsEnabled) return
    // Network interruptions are expected for SSE. Retry a few times without disturbing the page.
    if (reconnectAttempts < 3) {
      reconnectAttempts += 1
      reconnectTimer = window.setTimeout(startEvents, reconnectAttempts * 1500)
    }
  }
}
function decrementUnread() {
  unread.value = Math.max(0, unread.value - 1)
}
onMounted(async () => {
  try {
    const notes = await api<{ read: boolean }[]>({ url: '/api/notifications' })
    unread.value = notes.filter((n) => !n.read).length
  } catch {
    /* page owns its error state */
  }
  startEvents()
  window.addEventListener('notification-read', decrementUnread)
})
onBeforeUnmount(() => {
  eventsEnabled = false
  aborter?.abort()
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  window.removeEventListener('notification-read', decrementUnread)
})
</script>
<template>
  <el-container class="shell" :class="{ collapsed }"
    ><el-aside class="side" :width="collapsed ? '68px' : '228px'"
      ><div class="brand">
        <div class="brand-mark"><School /></div>
        <span v-show="!collapsed">知行教务</span>
      </div>
      <el-menu class="navigation" :collapse="collapsed" :default-active="$route.path" router
        ><el-menu-item v-for="item in menus" :key="item.path" :index="item.path"
          ><el-icon><component :is="item.icon" /></el-icon
          ><template #title>{{ item.label }}</template></el-menu-item
        ></el-menu
      >
      <div class="side-footer">
        <el-button text class="collapse" @click="collapsed = !collapsed"
          ><el-icon><Fold v-if="!collapsed" /><MenuIcon v-else /></el-icon
          ><span v-show="!collapsed">收起导航</span></el-button
        >
      </div></el-aside
    ><el-container
      ><el-header class="topbar"
        ><el-button class="mobile-menu" text @click="collapsed = !collapsed"
          ><el-icon><MenuIcon /></el-icon
        ></el-button>
        <div class="topbar-spacer" />
        <el-badge :hidden="!unread" :value="unread" class="notice-badge"
          ><el-button text aria-label="通知中心" @click="router.push('/notifications')"
            ><el-icon :size="19"><Bell /></el-icon></el-button></el-badge
        ><el-dropdown
          @command="
            (command: string) => (command === 'logout' ? logout() : router.push('/profile'))
          "
          ><button class="user-trigger">
            <el-avatar :size="32" :src="auth.user?.photo"
              ><el-icon><User /></el-icon></el-avatar
            ><span class="user-name">{{ auth.user?.name }}</span
            ><small>{{ roleText[auth.user?.role || 'STUDENT'] }}</small></button
          ><template #dropdown
            ><el-dropdown-menu
              ><el-dropdown-item command="profile">个人设置</el-dropdown-item
              ><el-dropdown-item divided command="logout"
                >退出登录</el-dropdown-item
              ></el-dropdown-menu
            ></template
          ></el-dropdown
        ></el-header
      ><el-main class="content"><RouterView /></el-main></el-container
  ></el-container>
</template>
<style scoped>
.shell {
  min-height: 100vh;
}
.side {
  position: relative;
  background: #123d2d;
  transition: width 0.2s;
  overflow: hidden;
}
.brand {
  height: 70px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 19px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 31px;
  height: 31px;
  border-radius: 9px;
  background: #d8f0e4;
  color: #176346;
}
.navigation {
  border-right: 0;
  background: transparent;
}
.navigation :deep(.el-menu-item) {
  height: 50px;
  margin: 4px 10px;
  border-radius: 8px;
  color: #bfd4ca;
}
.navigation :deep(.el-menu-item:hover) {
  background: #1c513d;
  color: #fff;
}
.navigation :deep(.el-menu-item.is-active) {
  background: #277155;
  color: #fff;
  font-weight: 700;
}
.navigation :deep(.el-menu-item .el-icon) {
  color: inherit;
}
.side-footer {
  position: absolute;
  bottom: 16px;
  left: 10px;
  right: 10px;
}
.collapse {
  width: 100%;
  justify-content: flex-start;
  color: #bfd4ca;
}
.collapsed .collapse {
  justify-content: center;
}
.topbar {
  height: 70px;
  display: flex;
  align-items: center;
  padding: 0 28px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}
.topbar-spacer {
  flex: 1;
}
.notice-badge {
  margin-right: 16px;
}
.user-trigger {
  border: 0;
  background: none;
  display: grid;
  grid-template-columns: auto auto;
  column-gap: 9px;
  align-items: center;
  text-align: left;
  cursor: pointer;
}
.user-name {
  font-size: 13px;
  font-weight: 700;
}
.user-trigger small {
  grid-column: 2;
  color: var(--muted);
  font-size: 11px;
}
.content {
  padding: 28px;
  background: var(--canvas);
}
.mobile-menu {
  display: none;
}
@media (max-width: 760px) {
  .side {
    position: fixed;
    z-index: 20;
    height: 100vh;
    width: 228px !important;
    transform: translateX(-100%);
    transition: transform 0.2s;
  }
  .shell.collapsed .side {
    transform: translateX(0);
  }
  .shell:not(.collapsed) .side {
    width: 228px !important;
  }
  .topbar {
    padding: 0 16px;
  }
  .mobile-menu {
    display: inline-flex;
  }
  .content {
    padding: 18px 14px;
  }
  .user-name,
  .user-trigger small {
    display: none;
  }
}
</style>
