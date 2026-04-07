import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const routes = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/DashboardView.vue'),
  },
  {
    path: '/weather',
    name: 'Weather',
    component: () => import('@/views/WeatherView.vue'),
  },
  {
    path: '/seismic',
    name: 'Seismic',
    component: () => import('@/views/SeismicView.vue'),
  },
  {
    path: '/hydrology',
    name: 'Hydrology',
    component: () => import('@/views/HydrologyView.vue'),
  },
  {
    path: '/spaceweather',
    name: 'SpaceWeather',
    component: () => import('@/views/SpaceWeatherView.vue'),
  },
  {
    path: '/alerts',
    name: 'Alerts',
    component: () => import('@/views/AlertsView.vue'),
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresAdmin: true },
  },
  {
    path: '/logs',
    name: 'Logs',
    component: () => import('@/views/LogsView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true

  const { check, isAdmin } = useAuth()
  const authenticated = await check()

  if (!authenticated) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !isAdmin()) {
    return { name: 'Dashboard' }
  }

  return true
})

export default router
