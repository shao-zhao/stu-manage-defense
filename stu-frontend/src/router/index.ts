import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { Role } from '@/types'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/dashboard' },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginPage.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/views/AppPage.vue'),
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
        },
        {
          path: 'students',
          name: 'students',
          component: () => import('@/views/StudentView.vue'),
          meta: { roles: ['ADMIN'] satisfies Role[] },
        },
        {
          path: 'staff',
          name: 'staff',
          component: () => import('@/views/StaffView.vue'),
          meta: { roles: ['ADMIN'] satisfies Role[] },
        },
        { path: 'courses', name: 'courses', component: () => import('@/views/CoursesView.vue') },
        {
          path: 'enrollments',
          name: 'enrollments',
          component: () => import('@/views/EnrollmentsView.vue'),
          meta: { roles: ['STUDENT'] satisfies Role[] },
        },
        { path: 'grades', name: 'grades', component: () => import('@/views/GradesView.vue') },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/NotificationsView.vue'),
        },
        { path: 'media', name: 'media', component: () => import('@/views/MediaView.vue') },
        { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return auth.isLoggedIn ? '/dashboard' : true
  if (!localStorage.getItem('stu_manage_token')) return '/login'
  if (!auth.user) {
    try {
      await auth.refresh()
    } catch {
      return '/login'
    }
  }
  const roles = to.meta.roles as Role[] | undefined
  if (roles && (!auth.user || !roles.includes(auth.user.role))) return '/dashboard'
  return true
})
export default router
