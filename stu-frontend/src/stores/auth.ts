import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/util/request'
import type { Role, User } from '@/types'
type LoginPayload = { token: string; user: User }
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(JSON.parse(localStorage.getItem('stu_manage_user') || 'null'))
  const isLoggedIn = computed(() => Boolean(localStorage.getItem('stu_manage_token') && user.value))
  function persist(next: User | null) {
    user.value = next
    next
      ? localStorage.setItem('stu_manage_user', JSON.stringify(next))
      : localStorage.removeItem('stu_manage_user')
  }
  async function login(username: string, password: string, role: Role) {
    const result = await api<LoginPayload>({
      url: '/api/auth/login',
      method: 'POST',
      data: { username, password, role },
    })
    localStorage.setItem('stu_manage_token', result.token)
    persist(result.user)
  }
  async function refresh() {
    persist(await api<User>({ url: '/api/auth/me' }))
  }
  async function logout() {
    try {
      await api<void>({ url: '/api/auth/logout', method: 'POST' })
    } catch {
      /* local logout still succeeds */
    }
    localStorage.removeItem('stu_manage_token')
    persist(null)
  }
  return { user, isLoggedIn, login, refresh, logout, persist }
})
