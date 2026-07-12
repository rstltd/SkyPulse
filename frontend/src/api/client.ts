import axios from 'axios'
import router from '@/router'
import { reportApiFailure } from '@/composables/useDataStatus'

const api = axios.create({
  baseURL: import.meta.env.BASE_URL + 'api/v1',
  withCredentials: true,
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status ?? null
    if (status === 401) {
      const currentPath = router.currentRoute.value.fullPath
      if (currentPath !== '/login') {
        router.push({ name: 'Login', query: { redirect: currentPath } })
      }
    } else {
      // Make data-fetch failures visible instead of silently showing stale/empty data.
      reportApiFailure(error.config?.url ?? 'unknown', status)
    }
    return Promise.reject(error)
  }
)

export default api
