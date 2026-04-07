import axios from 'axios'
import type { ApiResponse, LoginResponse } from '@/types'

const authApi = axios.create({
  baseURL: import.meta.env.BASE_URL + 'auth',
  withCredentials: true,
})

export const login = (username: string, password: string) =>
  authApi.post<ApiResponse<LoginResponse>>('/login', { username, password })

export const logout = () =>
  authApi.post<ApiResponse<string>>('/logout')

export const checkSession = () =>
  authApi.get<ApiResponse<LoginResponse>>('/me')
