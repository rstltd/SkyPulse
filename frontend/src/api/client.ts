import axios from 'axios'
import router from '@/router'

const api = axios.create({
  baseURL: import.meta.env.BASE_URL + 'api/v1',
  withCredentials: true,
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const currentPath = router.currentRoute.value.fullPath
      if (currentPath !== '/login') {
        router.push({ name: 'Login', query: { redirect: currentPath } })
      }
    }
    return Promise.reject(error)
  }
)

export default api
