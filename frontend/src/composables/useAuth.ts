import { ref } from 'vue'
import { checkSession, login as apiLogin, logout as apiLogout } from '@/api/auth'
import type { LoginResponse } from '@/types'

const user = ref<LoginResponse | null>(null)
const checked = ref(false)

export function useAuth() {
  const isAuthenticated = () => user.value !== null
  const isAdmin = () => user.value?.role === 'ADMIN'

  const check = async (): Promise<boolean> => {
    if (checked.value && user.value) return true
    try {
      const { data } = await checkSession()
      if (data.success && data.data) {
        user.value = data.data
        checked.value = true
        return true
      }
    } catch {
      // not authenticated
    }
    user.value = null
    checked.value = true
    return false
  }

  const login = async (username: string, password: string): Promise<string | null> => {
    try {
      const { data } = await apiLogin(username, password)
      if (data.success && data.data) {
        user.value = data.data
        checked.value = true
        return null
      }
      return data.message || 'Login failed'
    } catch (err: any) {
      return err.response?.data?.message || 'Login failed'
    }
  }

  const logout = async () => {
    try {
      await apiLogout()
    } catch {
      // ignore
    }
    user.value = null
    checked.value = false
  }

  return { user, isAuthenticated, isAdmin, check, login, logout }
}
