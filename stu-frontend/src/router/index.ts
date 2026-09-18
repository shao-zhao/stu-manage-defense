import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      component: () => import('../views/LoginPage.vue'),
    },
    {
      path: '/app-page',
      redirect:'/student',
      component: () => import('../views/AppPage.vue'),
      children: [
        {
          path: '/home',
          component: HomeView,
        },
        {
          path: '/about',
          component: () => import('../views/AboutView.vue'),
        },
        {
          path: '/student',
          component: () => import('../views/StudentView.vue'),
        },
        {
          path: '/staff',
          component: () => import('../views/StaffView.vue'),
        },
        {
          path: '/prac',
          component: () => import('../views/PracView.vue'),
        },
      ],
    },
  ],
})

export default router
